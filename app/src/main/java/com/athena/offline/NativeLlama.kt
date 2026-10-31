package com.athena.offline

class NativeLlama {
    companion object {
        init { System.loadLibrary("athena_llama") }
    }
    external fun nativeLoad(modelPath: String): Boolean
    external fun nativeGenerate(prompt: String, maxTokens: Int, stageFilePath: String): String
    external fun nativeUnload()
}
