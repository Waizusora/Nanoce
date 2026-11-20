#include <tree_sitter/api.h>

#include <cassert>
#include <cstdint>
#include <string>

extern "C" const TSLanguage* tree_sitter_json();
extern "C" const TSLanguage* tree_sitter_c();
extern "C" const TSLanguage* tree_sitter_cpp();
extern "C" const TSLanguage* tree_sitter_rust();

namespace {

bool parses_without_errors(const TSLanguage* language, const std::string& source) {
    TSParser* parser = ts_parser_new();
    assert(parser != nullptr);
    assert(ts_parser_set_language(parser, language));
    TSTree* tree = ts_parser_parse_string(
        parser, nullptr, source.data(), static_cast<uint32_t>(source.size()));
    assert(tree != nullptr);
    const bool valid = !ts_node_has_error(ts_tree_root_node(tree));
    ts_tree_delete(tree);
    ts_parser_delete(parser);
    return valid;
}

void expect_language(const TSLanguage* language,
                     const std::string& valid,
                     const std::string& invalid) {
    assert(parses_without_errors(language, valid));
    assert(!parses_without_errors(language, invalid));
}


void expect_query(const TSLanguage* language, const std::string& query_source,
                  const std::string& source,
                  const std::string& expected_categories) {
    uint32_t error_offset = 0;
    TSQueryError error_type = TSQueryErrorNone;
    TSQuery* query = ts_query_new(language, query_source.data(),
        static_cast<uint32_t>(query_source.size()), &error_offset, &error_type);
    assert(query != nullptr);
    TSParser* parser = ts_parser_new();
    assert(parser != nullptr);
    assert(ts_parser_set_language(parser, language));
    TSTree* tree = ts_parser_parse_string(parser, nullptr, source.data(),
        static_cast<uint32_t>(source.size()));
    assert(tree != nullptr);
    TSQueryCursor* cursor = ts_query_cursor_new();
    assert(cursor != nullptr);
    ts_query_cursor_exec(cursor, query, ts_tree_root_node(tree));

    std::string found_categories;
    TSQueryMatch match{};
    uint32_t capture_index = 0;
    while (ts_query_cursor_next_capture(cursor, &match, &capture_index)) {
        const TSQueryCapture& capture = match.captures[capture_index];
        uint32_t name_length = 0;
        const char* name = ts_query_capture_name_for_id(
            query, capture.index, &name_length);
        const std::string category(name, name_length);
        if (found_categories.find("|" + category + "|") == std::string::npos) {
            found_categories += "|" + category + "|";
        }
        assert(ts_node_end_byte(capture.node) > ts_node_start_byte(capture.node));
    }

    size_t expected_start = 0;
    while (expected_start < expected_categories.size()) {
        const size_t separator = expected_categories.find(',', expected_start);
        const std::string category = expected_categories.substr(
            expected_start, separator == std::string::npos
                ? std::string::npos : separator - expected_start);
        assert(found_categories.find("|" + category + "|") != std::string::npos);
        if (separator == std::string::npos) break;
        expected_start = separator + 1;
    }

    ts_query_cursor_delete(cursor);
    ts_tree_delete(tree);
    ts_parser_delete(parser);
    ts_query_delete(query);
}

}  // namespace

int main() {
    expect_language(
        tree_sitter_json(),
        R"({"name":"Nanoce","enabled":true,"items":[1,2,3]})",
        R"({"name":,"enabled":true})");

    expect_language(
        tree_sitter_c(),
        "int main(void) { return 0; }",
        "int main(void) { return ;");

    expect_language(
        tree_sitter_cpp(),
        "#include <string>\nint main() { std::string value = \"Nanoce\"; return 0; }",
        "int main() { std::string value = ;");

    expect_language(
        tree_sitter_rust(),
        "fn main() { let value: &str = \"Nanoce\"; println!(\"{}\", value); }",
        "fn main() { let value: = ;");

    const std::string unicode_rust =
        "fn main() {\n    let café = \"編輯器\";\n    println!(\"{}\", café);\n}";
    assert(parses_without_errors(tree_sitter_rust(), unicode_rust));


    expect_query(tree_sitter_json(),
        "(pair key: (string) @property) (string) @string (number) @number "
        "[(null) (true) (false)] @constant",
        R"({"name":"Nanoce","enabled":true,"count":3})",
        "property,string,number,constant");

    expect_query(tree_sitter_c(),
        "(identifier) @variable (type_identifier) @type "
        "(primitive_type) @type (string_literal) @string "
        "(comment) @comment (number_literal) @number \"return\" @keyword",
        "int main(void) { return 0; }",
        "variable,type,keyword,number");

    expect_query(tree_sitter_cpp(),
        "(identifier) @variable (type_identifier) @type "
        "(primitive_type) @type (string_literal) @string "
        "(comment) @comment (number_literal) @number \"return\" @keyword",
        "int main() { return 0; }",
        "variable,type,keyword,number");

    expect_query(tree_sitter_rust(),
        "(identifier) @variable (type_identifier) @type "
        "(primitive_type) @type (string_literal) @string "
        "(raw_string_literal) @string (line_comment) @comment "
        "(block_comment) @comment (integer_literal) @number "
        "(float_literal) @number \"fn\" @keyword \"let\" @keyword",
        "fn main() { let value = 42; }",
        "variable,keyword,number");

    return 0;
}
