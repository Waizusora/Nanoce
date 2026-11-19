#include <jni.h>
#include <tree_sitter/api.h>

#include <algorithm>
#include <cstdio>
#include <cstdint>
#include <string>

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

namespace {

void append_utf8(std::string& output, uint32_t codepoint) {
    if (codepoint <= 0x7F) {
        output.push_back(static_cast<char>(codepoint));
    } else if (codepoint <= 0x7FF) {
        output.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
        output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
    } else if (codepoint <= 0xFFFF) {
        output.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
        output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
    } else {
        output.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
        output.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
    }
}

uint32_t utf16_column(const std::string& source, uint32_t row, uint32_t byte_column) {
    size_t line_start = 0;
    for (uint32_t current = 0; current < row; ++current) {
        const size_t newline = source.find('\n', line_start);
        if (newline == std::string::npos) return 1;
        line_start = newline + 1;
    }
    const size_t line_end = source.find('\n', line_start);
    const size_t available = (line_end == std::string::npos ? source.size() : line_end) - line_start;
    const size_t limit = line_start + std::min<size_t>(byte_column, available);
    uint32_t units = 0;
    for (size_t index = line_start; index < limit;) {
        const unsigned char lead = static_cast<unsigned char>(source[index]);
        const size_t width = lead < 0x80 ? 1 : (lead & 0xE0) == 0xC0 ? 2 :
                             (lead & 0xF0) == 0xE0 ? 3 : 4;
        if (index + width > limit) break;
        units += width == 4 ? 2 : 1;
        index += width;
    }
    return units + 1;
}
std::string java_string_to_utf8(JNIEnv* env, jstring source) {
    const jsize length = env->GetStringLength(source);
    const jchar* chars = env->GetStringChars(source, nullptr);
    if (chars == nullptr) return {};

    std::string output;
    output.reserve(static_cast<size_t>(length) * 3);
    for (jsize index = 0; index < length; ++index) {
        uint32_t codepoint = chars[index];
        if (codepoint >= 0xD800 && codepoint <= 0xDBFF &&
            index + 1 < length && chars[index + 1] >= 0xDC00 &&
            chars[index + 1] <= 0xDFFF) {
            codepoint = 0x10000 + ((codepoint - 0xD800) << 10) +
                        (static_cast<uint32_t>(chars[++index]) - 0xDC00);
        } else if (codepoint >= 0xD800 && codepoint <= 0xDFFF) {
            codepoint = 0xFFFD;
        }
        append_utf8(output, codepoint);
    }
    env->ReleaseStringChars(source, chars);
    return output;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_waizusora_nanoce_NativeBridge_nativeJsonDiagnostics(
    JNIEnv* env,
    jobject /* thiz */,
    jstring source
) {
    if (source == nullptr) {
        return env->NewStringUTF("ERROR:1:1");
    }

    std::string utf8 = java_string_to_utf8(env, source);
    if (env->ExceptionCheck()) return nullptr;
    if (utf8.size() > UINT32_MAX) {
        return env->NewStringUTF("INPUT_TOO_LARGE");
    }

    TSParser* parser = ts_parser_new();
    if (parser == nullptr) {
        return env->NewStringUTF("PARSER_ERROR");
    }

    const bool language_set = ts_parser_set_language(parser, tree_sitter_json());
    TSTree* tree = language_set
        ? ts_parser_parse_string(parser, nullptr, utf8.data(),
              static_cast<uint32_t>(utf8.size()))
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
            const uint32_t column = utf16_column(utf8, point.row, point.column);
            char message[64];
            std::snprintf(message, sizeof(message), "ERROR:%u:%u",
                          point.row + 1, column);
            result = env->NewStringUTF(message);
        }
        ts_tree_delete(tree);
    }

    ts_parser_delete(parser);
    return result;
}
