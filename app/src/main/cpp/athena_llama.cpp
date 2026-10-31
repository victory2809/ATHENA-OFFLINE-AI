#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include "llama.h"

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_athena_offline_NativeLlama_nativeLoad(JNIEnv *env, jobject, jstring jpath) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_backend_init();
    auto mp = llama_model_default_params();
    mp.use_mmap = true;
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) return JNI_FALSE;
    auto cp = llama_context_default_params();
    cp.n_ctx = 2048;
    cp.n_batch = 256;
    cp.n_ubatch = 128;
    cp.n_threads = std::max(2, std::min(4, (int)sysconf(_SC_NPROCESSORS_ONLN) - 1));
    cp.n_threads_batch = cp.n_threads;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; return JNI_FALSE; }
    g_vocab = llama_model_get_vocab(g_model);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_athena_offline_NativeLlama_nativeGenerate(JNIEnv *env, jobject, jstring jprompt, jint maxTokens) {
    if (!g_model || !g_ctx || !g_vocab) return env->NewStringUTF("Model is not loaded.");
    const char *raw = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(raw);
    env->ReleaseStringUTFChars(jprompt, raw);
    llama_memory_clear(llama_get_memory(g_ctx), true);
    std::vector<llama_token> tokens(std::max<size_t>(prompt.size() + 16, 256));
    int32_t n = llama_tokenize(g_vocab, prompt.c_str(), (int32_t)prompt.size(), tokens.data(), (int32_t)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize((size_t)(-n));
        n = llama_tokenize(g_vocab, prompt.c_str(), (int32_t)prompt.size(), tokens.data(), (int32_t)tokens.size(), true, true);
    }
    if (n <= 0) return env->NewStringUTF("Could not tokenize prompt.");
    tokens.resize(n);
    auto batch = llama_batch_get_one(tokens.data(), n);
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("Prompt processing failed.");
    auto sp = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sp);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    std::string output;
    char piece[512];
    const int limit = std::max(1, std::min((int)maxTokens, 384));
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, token)) break;
        llama_sampler_accept(sampler, token);
        int len = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);
        if (len > 0) output.append(piece, (size_t)len);
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_athena_offline_NativeLlama_nativeUnload(JNIEnv *, jobject) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
}
