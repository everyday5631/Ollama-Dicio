package org.stypox.dicio.llm

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * [LlmEngine] backed by native llama.cpp through the JNI bridge in `app/src/main/cpp/llama-jni.cpp`.
 *
 * llama.cpp contexts are not thread-safe, so every native call (load, generate, free) is
 * serialized onto a single dedicated thread via [nativeDispatcher]. Generation streams one token
 * per [nativeNextToken] call and stops early as soon as a complete tool-call JSON object is
 * detected in the output.
 *
 * If the native library is missing (llama.cpp submodule not checked out — see docs/local-llm.md),
 * [nativeAvailable] is false and every operation reports a clear error instead of crashing.
 */
class LlamaCppEngine : LlmEngine {

    private val singleThread = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llama-cpp").apply { isDaemon = true }
    }
    private val nativeDispatcher: CoroutineDispatcher = singleThread.asCoroutineDispatcher()

    private val _state = MutableStateFlow<LlmEngineState>(LlmEngineState.Unloaded)
    override val state: StateFlow<LlmEngineState> = _state

    /** Native model handle (0 == none loaded). Only touched on [nativeDispatcher]. */
    private var handle: Long = 0L

    /** Absolute path of the currently loaded model, to make [ensureLoaded] idempotent. */
    private var loadedPath: String? = null

    /**
     * The turn format the loaded model expects. Set by [GgufModelManager] from the configured model
     * reference before loading, because the file on disk is always called `llm-model.gguf` and so
     * says nothing about which family it came from.
     */
    @Volatile
    override var promptStyle: PromptStyle = PromptStyle.CHAT_ML

    override suspend fun ensureLoaded(modelPath: String) {
        if (!nativeAvailable) {
            _state.value = LlmEngineState.Error(
                IllegalStateException("Native llama.cpp library not available")
            )
            throw IllegalStateException("Native llama.cpp library not available")
        }
        withContext(nativeDispatcher) {
            if (handle != 0L && loadedPath == modelPath) {
                _state.value = LlmEngineState.Loaded
                return@withContext
            }
            _state.value = LlmEngineState.Loading
            if (handle != 0L) {
                nativeFreeModel(handle)
                handle = 0L
                loadedPath = null
            }
            try {
                if (!backendInitialized) {
                    nativeBackendInit()
                    backendInitialized = true
                }
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
                val h = nativeLoadModel(modelPath, N_CTX, threads, N_GPU_LAYERS)
                if (h == 0L) {
                    throw IllegalStateException("nativeLoadModel returned 0 for $modelPath")
                }
                handle = h
                loadedPath = modelPath
                _state.value = LlmEngineState.Loaded
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load model", t)
                _state.value = LlmEngineState.Error(t)
                throw t
            }
        }
    }

    override fun unload() {
        singleThread.execute {
            if (handle != 0L) {
                nativeFreeModel(handle)
                handle = 0L
                loadedPath = null
            }
            _state.value = LlmEngineState.Unloaded
        }
    }

    override fun generate(messages: List<LlmMessage>, tools: List<LlmToolDef>): Flow<LlmEvent> =
        callbackFlow {
            if (!nativeAvailable || handle == 0L) {
                trySend(LlmEvent.Error(IllegalStateException("No model loaded")))
                close()
                return@callbackFlow
            }

            val prompt = ChatFormat.build(messages, tools, promptStyle)
            val accumulated = StringBuilder()
            var finished = false

            try {
                val started = nativeStartCompletion(handle, prompt, N_PREDICT)
                if (!started) {
                    trySend(LlmEvent.Error(IllegalStateException("nativeStartCompletion failed")))
                    close()
                    return@callbackFlow
                }

                while (true) {
                    val piece = nativeNextToken(handle) ?: break
                    accumulated.append(piece)
                    trySend(LlmEvent.Token(piece))

                    // stop early if the model has emitted a complete tool-call object
                    if (tools.isNotEmpty() &&
                        ToolCallParser.indexOfCompleteJsonObject(accumulated.toString()) >= 0
                    ) {
                        val call = ToolCallParser.parse(accumulated.toString())
                        if (call != null) {
                            nativeStopCompletion(handle)
                            trySend(LlmEvent.ToolCall(call))
                            finished = true
                            break
                        }
                    }
                }

                if (!finished) {
                    // a tool call may also appear only in the completed text
                    val call = if (tools.isNotEmpty()) {
                        ToolCallParser.parse(accumulated.toString())
                    } else {
                        null
                    }
                    if (call != null) {
                        trySend(LlmEvent.ToolCall(call))
                    } else {
                        trySend(LlmEvent.Done(accumulated.toString().trim()))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Generation error", t)
                trySend(LlmEvent.Error(t))
            } finally {
                close()
            }

            awaitClose {
                // if the collector cancels, stop the native generation loop
                singleThread.execute {
                    if (handle != 0L) nativeStopCompletion(handle)
                }
            }
        }.flowOn(nativeDispatcher)

    // ----- native methods (implemented in app/src/main/cpp/llama-jni.cpp) -----
    private external fun nativeBackendInit()
    private external fun nativeBackendFree()
    private external fun nativeLoadModel(
        path: String, nCtx: Int, nThreads: Int, nGpuLayers: Int
    ): Long
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeStartCompletion(handle: Long, prompt: String, nPredict: Int): Boolean
    private external fun nativeNextToken(handle: Long): String?
    private external fun nativeStopCompletion(handle: Long)

    companion object {
        private val TAG = LlamaCppEngine::class.simpleName

        /** Context window size. 2048 keeps memory modest; raise for longer conversations. */
        private const val N_CTX = 2048

        /** Max tokens generated per turn. */
        private const val N_PREDICT = 384

        /** GPU offload layers. 0 = CPU only, which is the realistic default on Android. */
        private const val N_GPU_LAYERS = 0

        private var backendInitialized = false

        /** Whether the native library loaded successfully. */
        val nativeAvailable: Boolean = try {
            System.loadLibrary("dicio_llm")
            true
        } catch (t: Throwable) {
            Log.w("LlamaCppEngine", "Native llama.cpp library not available: ${t.message}")
            false
        }
    }
}
