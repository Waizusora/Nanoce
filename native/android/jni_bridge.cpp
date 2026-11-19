#include <jni.h>
#include <tree_sitter/api.h>

#include "nanoce/nanoce.hpp"

extern "C" const TSLanguage* tree_sitter_json();

extern "C" JNIEXPORT jstring JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeVersion(
    JNIEnv* env,
    jobject /* thiz */
) {
    const auto version = nanoce::version();
    return env->NewStringUTF(version.data());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeTreeSitterLanguageAbi(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return TREE_SITTER_LANGUAGE_VERSION;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeValidateJson(
    JNIEnv* env,
    jobject /* thiz */,
    jstring source
) {
    if (source == nullptr) {
        return JNI_FALSE;
    }

    const char* utf8 = env->GetStringUTFChars(source, nullptr);
    if (utf8 == nullptr) {
        return JNI_FALSE;
    }

    TSParser* parser = ts_parser_new();
    if (parser == nullptr) {
        env->ReleaseStringUTFChars(source, utf8);
        return JNI_FALSE;
    }

    const bool language_set = ts_parser_set_language(parser, tree_sitter_json());
    TSTree* tree = language_set
        ? ts_parser_parse_string(parser, nullptr, utf8,
              static_cast<uint32_t>(env->GetStringUTFLength(source)))
        : nullptr;

    const bool valid = tree != nullptr &&
        !ts_node_has_error(ts_tree_root_node(tree));

    if (tree != nullptr) {
        ts_tree_delete(tree);
    }
    ts_parser_delete(parser);
    env->ReleaseStringUTFChars(source, utf8);
    return valid ? JNI_TRUE : JNI_FALSE;
}
