package com.waizusora.nanoce

internal class NativeBridge {
    external fun nativeVersion(): String
    external fun nativeTreeSitterLanguageAbi(): Int

    companion object {
        init {
            System.loadLibrary("nanoce_android")
        }
    }
}
