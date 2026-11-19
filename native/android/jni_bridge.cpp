#include <jni.h>
#include <tree_sitter/api.h>

#include <cstdio>

#include "nanoce/nanoce.hpp"

extern "C" const TSLanguage* tree_sitter_json();

namespace {

TSNode first_error_node(TSNode node) {
    if (ts_node_is_error(node) || ts_node_is_missing(node)) {
        return node;
    }

    const uint32_t child_count = ts_node_child_count(node);
    for (uint32_t index = 0; index < child_count; ++index) {
        TSNode child = first_error_node(ts_node_child(node, index));
        if (!ts_node_is_null(child)) {
            return child;
        }
    }
    return ts_node_is_null(node) && ts_node_has_error(node) ? node : TSNode{};
}

}  // namespace

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

extern "C" JNIEXPORT jstring JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeJsonDiagnostics(
    JNIEnv* env,
    jobject /* thiz */,
    jstring source
) {
    if (source == nullptr) {
        return env->NewStringUTF("ERROR:1:1");
    }

    const char* utf8 = env->GetStringUTFChars(source, nullptr);
    if (utf8 == nullptr) {
        return nullptr;
    }

    TSParser* parser = ts_parser_new();
    if (parser == nullptr) {
        env->ReleaseStringUTFChars(source, utf8);
        return env->NewStringUTF("PARSER_ERROR");
    }

    const bool language_set = ts_parser_set_language(parser, tree_sitter_json());
    TSTree* tree = language_set
        ? ts_parser_parse_string(parser, nullptr, utf8,
              static_cast<uint32_t>(env->GetStringUTFLength(source)))
        : nullptr;

    jstring result = nullptr;
    if (tree == nullptr) {
        result = env->NewStringUTF("PARSER_ERROR");
    } else {
        const TSNode root = ts_tree_root_node(tree);
        if (!ts_node_has_error(root)) {
            result = env->NewStringUTF("OK");
        } else {
            TSNode error = first_error_node(root);
            const TSPoint point = ts_node_start_point(error);
            char message[64];
            std::snprintf(message, sizeof(message), "ERROR:%u:%u",
                          point.row + 1, point.column + 1);
            result = env->NewStringUTF(message);
        }
        ts_tree_delete(tree);
    }

    ts_parser_delete(parser);
    env->ReleaseStringUTFChars(source, utf8);
    return result;
}
