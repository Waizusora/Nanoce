package com.waizusora.nanoce

internal class NativeBridge {
    external fun nativeVersion(): String

    companion object {
        init {
            System.loadLibrary("nanoce_android")
        }
    }
}
