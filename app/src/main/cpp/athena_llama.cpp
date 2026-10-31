#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include <mutex>
#include <cstdio>
#include "llama.h"

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;
static bool g_backend_initialized = false;
static std::mutex g_engine_mutex;

static void write_stage(const std::string &path, const char *stage) {
    if (path.empty()) return;
    FILE *file = fopen(path.c_str(), "w");
    if (file) { fputs(stage, file); fflush(file); fclose(file); }
}

static std::string to_utf8(JNIEnv *env, jstring value) {
    std::string out;
    if (!value) return out;
    const jchar *chars = env->GetStringChars(value, nullptr);
    const jsize len = env->GetStringLength(value);
    for (jsize i = 0; i < len; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < len) {
            uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            }
        }
        if (cp <= 0x7F) out.push_back((char)cp);
        else if (cp <= 0x7FF) {
            out.push_back((char)(0xC0 | (cp >> 6)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            out.push_back((char)(0xE0 | (cp >> 12)));
            out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        } else {
            out.push_back((char)(0xF0 | (cp >> 18)));
            out.push_back((char)(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back((char)(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back((char)(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

static jstring from_utf8(JNIEnv *env, const std::string &value) {
    std::vector<jchar> out;
    for (size_t i = 0; i < value.size();) {
        uint32_t cp = (unsigned char)value[i++];
        if ((cp & 0xE0) == 0xC0 && i < value.size()) {
            cp = ((cp & 0x1F) << 6) | ((unsigned char)value[i++] & 0x3F);
        } else if ((cp & 0xF0) == 0xE0 && i + 1 < value.size()) {
            cp = ((cp & 0x0F) << 12) | (((unsigned char)value[i++] & 0x3F) << 6) |
                 ((unsigned char)value[i++] & 0x3F);
        } else if ((cp & 0xF8) == 0xF0 && i + 2 < value.size()) {
            cp = ((cp & 0x07) << 18) | (((unsigned char)value[i++] & 0x3F) << 12) |
                 (((unsigned char)value[i++] & 0x3F) << 6) | ((unsigned char)value[i++] & 0x3F);
        }
        if (cp <= 0xFFFF) out.push_back((jchar)cp);
        else {
            cp -= 0x10000;
            out.push_back((jchar)(0xD800 + (cp >> 10)));
            out.push_back((jchar)(0xDC00 + (cp & 0x3FF)));
        }
    }
    return env->NewString(out.data(), (jsize)out.size());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_athena_offline_NativeLlama_nativeLoad(JNIEnv *env, jobject, jstring jpath) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
    const std::string path = to_utf8(env, jpath);
    if (!g_backend_initialized) { llama_backend_init(); g_backend_initialized = true; }
    auto mp = llama_model_default_params();
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) return JNI_FALSE;
    auto cp = llama_context_default_params();
    cp.n_ctx = 256;
    cp.n_batch = 16;
    cp.n_ubatch = 8;
    cp.n_threads = 2;
    cp.n_threads_batch = 2;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; return JNI_FALSE; }
    g_vocab = llama_model_get_vocab(g_model);
    return JNI_TRUE;
}

// Decode tokens in bounded micro-batches. The context is configured with
// n_batch=16 and n_ubatch=8; passing a whole prompt as one batch can exceed
// those limits and crash some mobile builds inside llama_decode().
static int decode_tokens(llama_context *ctx, const llama_token *tokens, int count, int start_pos) {
    const int micro_batch = 8;
    for (int offset = 0; offset < count; offset += micro_batch) {
        const int chunk = std::min(micro_batch, count - offset);
        llama_batch batch = llama_batch_init(chunk, 0, 1);
        if (!batch.token || !batch.pos || !batch.n_seq_id || !batch.seq_id || !batch.logits) {
            llama_batch_free(batch);
            return -1;
        }
        for (int i = 0; i < chunk; ++i) {
            batch.token[i] = tokens[offset + i];
            batch.pos[i] = start_pos + offset + i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = (i == chunk - 1) ? 1 : 0;
        }
        const int result = llama_decode(ctx, batch);
        llama_batch_free(batch);
        if (result != 0) return result;
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_athena_offline_NativeLlama_nativeGenerate(JNIEnv *env, jobject, jstring jprompt, jint maxTokens, jstring jstagePath) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    const std::string stage_path = to_utf8(env, jstagePath);
    write_stage(stage_path, "entered_native_generate");
    if (!g_model || !g_ctx || !g_vocab) return from_utf8(env, "Model is not loaded.");
    const std::string prompt = to_utf8(env, jprompt);
    write_stage(stage_path, "converted_prompt");
    llama_memory_clear(llama_get_memory(g_ctx), true);
    std::vector<llama_token> tokens(std::max<size_t>(prompt.size() + 16, 256));
    int32_t n = llama_tokenize(g_vocab, prompt.c_str(), (int32_t)prompt.size(),
                              tokens.data(), (int32_t)tokens.size(), true, true);
    if (n < 0) {
        tokens.resize((size_t)(-n));
        n = llama_tokenize(g_vocab, prompt.c_str(), (int32_t)prompt.size(),
                           tokens.data(), (int32_t)tokens.size(), true, true);
    }
    if (n <= 0) { write_stage(stage_path, "tokenization_failed"); return from_utf8(env, "Could not tokenize the question."); }
    write_stage(stage_path, "tokenized_prompt");
    const int context = (int)llama_n_ctx(g_ctx);
    const int limit = std::max(1, std::min((int)maxTokens, 48));
    if (n >= context - 8 || n + limit >= context) {
        write_stage(stage_path, "prompt_exceeds_context");
        return from_utf8(env, "This question and its notes are too long for the phone's local context. Please shorten the question or use fewer notes.");
    }
    tokens.resize(n);
    write_stage(stage_path, "before_prompt_decode");
    const int prompt_result = decode_tokens(g_ctx, tokens.data(), n, 0);
    if (prompt_result != 0) {
        write_stage(stage_path, "prompt_decode_returned_error");
        return from_utf8(env, "Prompt processing failed (native code " + std::to_string(prompt_result) + "). Try a shorter question.");
    }
    write_stage(stage_path, "prompt_decoded");
    auto sp = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sp);
    if (!sampler) return from_utf8(env, "Could not initialize the text sampler.");
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    std::string output;
    char piece[512];
    write_stage(stage_path, "before_token_generation");
    for (int i = 0; i < limit; ++i) {
        write_stage(stage_path, "sampling_token");
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, token)) break;
        llama_sampler_accept(sampler, token);
        int len = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);
        if (len > 0) output.append(piece, (size_t)len);
        write_stage(stage_path, "decoding_generated_token");
        if (decode_tokens(g_ctx, &token, 1, n + i) != 0) {
            write_stage(stage_path, "generated_token_decode_error");
            break;
        }
    }
    llama_sampler_free(sampler);
    write_stage(stage_path, "generation_complete");
    return from_utf8(env, output);
}

extern "C" JNIEXPORT void JNICALL
Java_com_athena_offline_NativeLlama_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_vocab = nullptr;
}
