package com.waizusora.nanoce

internal class NativeBridge {
    external fun nativeVersion(): String
    external fun nativeTreeSitterLanguageAbi(): Int
    external fun nativeJsonDiagnostics(source: String): String
    external fun nativeCodeDiagnostics(source: String, language: String): String

    companion object {
        init {
            System.loadLibrary("nanoce_android")
        }
    }
}
