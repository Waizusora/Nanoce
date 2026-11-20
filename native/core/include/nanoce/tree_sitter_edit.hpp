#pragma once

#include <tree_sitter/api.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string>

namespace nanoce {

inline bool is_utf8_continuation(unsigned char value) {
    return (value & 0xC0) == 0x80;
}

inline TSPoint tree_sitter_point_at_byte(const std::string& source,
                                         size_t offset) {
    TSPoint point{0, 0};
    const size_t limit = std::min(offset, source.size());
    for (size_t index = 0; index < limit; ++index) {
        if (source[index] == '\n') {
            ++point.row;
            point.column = 0;
        } else {
            ++point.column;
        }
    }
    return point;
}

// Produces a single UTF-8-boundary-aligned edit suitable for ts_tree_edit().
inline TSInputEdit make_tree_sitter_edit(const std::string& old_source,
                                         const std::string& new_source) {
    size_t prefix = 0;
    const size_t common_limit = std::min(old_source.size(), new_source.size());
    while (prefix < common_limit && old_source[prefix] == new_source[prefix]) {
        ++prefix;
    }
    while (prefix > 0 &&
           ((prefix < old_source.size() && is_utf8_continuation(
                static_cast<unsigned char>(old_source[prefix]))) ||
            (prefix < new_source.size() && is_utf8_continuation(
                static_cast<unsigned char>(new_source[prefix]))))) {
        --prefix;
    }

    size_t suffix = 0;
    while (suffix < old_source.size() - prefix &&
           suffix < new_source.size() - prefix &&
           old_source[old_source.size() - suffix - 1] ==
               new_source[new_source.size() - suffix - 1]) {
        ++suffix;
    }
    while (suffix > 0) {
        const size_t old_end = old_source.size() - suffix;
        const size_t new_end = new_source.size() - suffix;
        const bool old_boundary = old_end == old_source.size() ||
            !is_utf8_continuation(static_cast<unsigned char>(old_source[old_end]));
        const bool new_boundary = new_end == new_source.size() ||
            !is_utf8_continuation(static_cast<unsigned char>(new_source[new_end]));
        if (old_boundary && new_boundary) break;
        --suffix;
    }

    const size_t old_end = old_source.size() - suffix;
    const size_t new_end = new_source.size() - suffix;
    TSInputEdit edit{};
    edit.start_byte = static_cast<uint32_t>(prefix);
    edit.old_end_byte = static_cast<uint32_t>(old_end);
    edit.new_end_byte = static_cast<uint32_t>(new_end);
    edit.start_point = tree_sitter_point_at_byte(old_source, prefix);
    edit.old_end_point = tree_sitter_point_at_byte(old_source, old_end);
    edit.new_end_point = tree_sitter_point_at_byte(new_source, new_end);
    return edit;
}

}  // namespace nanoce
