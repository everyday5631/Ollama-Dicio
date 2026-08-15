# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller build for Linux and Windows.

Models are deliberately NOT bundled: they are downloaded on first run and cached in the
user's data directory, which keeps this artifact around 80 MB rather than ~500 MB, and
lets the user switch models without a rebuild.
"""

block_cipher = None

a = Analysis(
    ["main.py"],
    pathex=["."],
    binaries=[],
    datas=[],
    # vosk and llama_cpp load native libraries through cffi/ctypes, which PyInstaller's
    # static analysis cannot see; naming them here makes sure they are collected
    hiddenimports=["vosk", "sounddevice", "cffi", "_cffi_backend", "llama_cpp"],
    hookspath=[],
    runtime_hooks=[],
    # PySide6 pulls in a great deal that a desktop assistant never touches
    excludes=["tkinter", "PySide6.QtWebEngineCore", "PySide6.Qt3DCore", "PySide6.QtCharts",
              "PySide6.QtQuick", "PySide6.QtQml", "matplotlib", "PIL"],
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="enclave-desktop",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,   # no console window on Windows
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name="enclave-desktop",
)
