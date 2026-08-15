package org.stypox.dicio.llm

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain

class MemoryIntentTest : StringSpec({

    "an explicit request to remember is recognised" {
        listOf(
            "could you remember that my favorite dish is a greek salad",
            "Remember that I live in Berlin",
            "please don't forget my birthday is in June",
            "keep in mind that I prefer metric units",
            "merk dir dass mein Lieblingsessen griechischer Salat ist",
            "ricorda che vivo a Roma",
            "recuerda que soy vegetariano",
            "n'oublie pas que je suis allergique aux noix",
        ).forEach { MemoryIntent.isRememberRequest(it) shouldBe true }
    }

    "merely mentioning something is not a request to remember" {
        // this is the case that used to fill the memory file with noise
        listOf(
            "I had a greek salad for lunch",
            "my favorite dish is a greek salad",
            "what's the weather like tomorrow",
            "set a timer for five minutes",
            "I live in Berlin",
            "tell me a joke",
            "",
            "   ",
        ).forEach { MemoryIntent.isRememberRequest(it) shouldBe false }
    }

    "forget requests are recognised separately" {
        MemoryIntent.isForgetRequest("forget everything you know about me") shouldBe true
        MemoryIntent.isForgetRequest("vergiss meinen Geburtstag") shouldBe true
        MemoryIntent.isForgetRequest("remember that I like tea") shouldBe false
    }
})

class PromptStyleTest : StringSpec({

    "gemma references select the gemma style" {
        PromptStyle.forModel("gemma3:270m") shouldBe PromptStyle.GEMMA
        PromptStyle.forModel("gemma2:2b") shouldBe PromptStyle.GEMMA
        PromptStyle.forModel("https://example.com/gemma-3-270m-it-Q4_K_M.gguf") shouldBe
            PromptStyle.GEMMA
    }

    "everything else falls back to ChatML" {
        PromptStyle.forModel("qwen2.5:0.5b") shouldBe PromptStyle.CHAT_ML
        PromptStyle.forModel("tinydolphin") shouldBe PromptStyle.CHAT_ML
        PromptStyle.forModel("") shouldBe PromptStyle.CHAT_ML
    }
})

class ChatFormatTest : StringSpec({

    val messages = listOf(
        LlmMessage(LlmRole.SYSTEM, "You are Enclave."),
        LlmMessage(LlmRole.USER, "what time is it"),
    )
    val tools = listOf(
        LlmToolDef("current_time", "Tell the time.", emptyList()),
    )

    "ChatML keeps a real system turn" {
        val prompt = ChatFormat.build(messages, tools, PromptStyle.CHAT_ML)
        prompt shouldContain "<|im_start|>system"
        prompt shouldContain "You are Enclave."
        prompt shouldContain "current_time"
        prompt shouldEndWith "<|im_start|>assistant\n"
    }

    "Gemma has no system turn and folds the system text into the first user turn" {
        val prompt = ChatFormat.build(messages, tools, PromptStyle.GEMMA)

        // the whole point: Gemma was never trained on a system role
        prompt shouldNotContain "<start_of_turn>system"
        prompt shouldNotContain "<|im_start|>"

        // system text and tool block ride along inside the user turn
        val userTurn = prompt.substringAfter("<start_of_turn>user\n").substringBefore("<end_of_turn>")
        userTurn shouldContain "You are Enclave."
        userTurn shouldContain "current_time"
        userTurn shouldContain "what time is it"

        prompt shouldEndWith "<start_of_turn>model\n"
    }

    "Gemma renders assistant turns as the model role" {
        val withHistory = listOf(
            LlmMessage(LlmRole.SYSTEM, "You are Enclave."),
            LlmMessage(LlmRole.USER, "hello"),
            LlmMessage(LlmRole.ASSISTANT, "Hi!"),
            LlmMessage(LlmRole.USER, "what time is it"),
        )
        val prompt = ChatFormat.build(withHistory, emptyList(), PromptStyle.GEMMA)
        prompt shouldContain "<start_of_turn>model\nHi!<end_of_turn>"
        // the system text attaches to the first user turn only, not to every one
        prompt.split("You are Enclave.").size shouldBe 2
    }

    "Gemma labels tool results" {
        val withTool = listOf(
            LlmMessage(LlmRole.USER, "what time is it"),
            LlmMessage(LlmRole.TOOL, "It is 5pm.", toolName = "current_time"),
        )
        val prompt = ChatFormat.build(withTool, emptyList(), PromptStyle.GEMMA)
        prompt shouldContain "Result of current_time: It is 5pm."
    }
})

class CpuTopologyTest : StringSpec({

    // peak clocks in kHz, as /sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq reports them
    val tensorG4 = listOf(3100000L) + List(3) { 2600000L } + List(4) { 1920000L }
    val snapdragon8Elite = List(2) { 4320000L } + List(6) { 3530000L }
    val snapdragon888 = listOf(2840000L) + List(3) { 2420000L } + List(4) { 1800000L }

    "a Tensor G4 uses its four fast cores, not all eight" {
        // the A520 efficiency cores would gate every layer boundary
        CpuTopology.selectThreadCount(tensorG4, 8) shouldBe 4
    }

    "an 8 Elite has no little cores, so it is capped by MAX_THREADS" {
        CpuTopology.selectThreadCount(snapdragon8Elite, 8) shouldBe 6
    }

    "an older big.LITTLE part also drops its little cluster" {
        CpuTopology.selectThreadCount(snapdragon888, 8) shouldBe 4
    }

    "a uniform CPU uses all of its cores" {
        CpuTopology.selectThreadCount(List(4) { 1800000L }, 4) shouldBe 4
    }

    "unreadable cpufreq falls back to half the cores" {
        CpuTopology.selectThreadCount(emptyList(), 8) shouldBe 4
        CpuTopology.selectThreadCount(emptyList(), 2) shouldBe 2
    }

    "the result is never below the minimum" {
        CpuTopology.selectThreadCount(listOf(1800000L), 1) shouldBe 2
    }
})
