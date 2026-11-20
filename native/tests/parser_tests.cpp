#include <tree_sitter/api.h>
#include "nanoce/tree_sitter_edit.hpp"

#include <cassert>
#include <cstdint>
#include <string>
#include <vector>
#include <cstdlib>

extern "C" const TSLanguage* tree_sitter_json();
extern "C" const TSLanguage* tree_sitter_c();
extern "C" const TSLanguage* tree_sitter_cpp();
extern "C" const TSLanguage* tree_sitter_rust();
extern "C" const TSLanguage* tree_sitter_python();
extern "C" const TSLanguage* tree_sitter_kotlin();

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


void expect_incremental_equivalent(const TSLanguage* language,
                                    const std::vector<std::string>& revisions) {
    assert(revisions.size() >= 2);
    TSParser* incremental_parser = ts_parser_new();
    TSParser* fresh_parser = ts_parser_new();
    assert(incremental_parser != nullptr && fresh_parser != nullptr);
    assert(ts_parser_set_language(incremental_parser, language));
    assert(ts_parser_set_language(fresh_parser, language));

    TSTree* incremental_tree = ts_parser_parse_string(
        incremental_parser, nullptr, revisions.front().data(),
        static_cast<uint32_t>(revisions.front().size()));
    assert(incremental_tree != nullptr);
    std::string previous = revisions.front();

    for (size_t index = 1; index < revisions.size(); ++index) {
        const std::string& current = revisions[index];
        const TSInputEdit edit = nanoce::make_tree_sitter_edit(previous, current);
        assert(edit.start_byte <= edit.old_end_byte);
        assert(edit.start_byte <= edit.new_end_byte);
        assert(edit.old_end_byte <= previous.size());
        assert(edit.new_end_byte <= current.size());
        ts_tree_edit(incremental_tree, &edit);

        TSTree* updated = ts_parser_parse_string(incremental_parser,
            incremental_tree, current.data(), static_cast<uint32_t>(current.size()));
        assert(updated != nullptr);
        ts_tree_delete(incremental_tree);
        incremental_tree = updated;

        TSTree* fresh_tree = ts_parser_parse_string(fresh_parser, nullptr,
            current.data(), static_cast<uint32_t>(current.size()));
        assert(fresh_tree != nullptr);
        char* incremental_sexp = ts_node_string(ts_tree_root_node(incremental_tree));
        char* fresh_sexp = ts_node_string(ts_tree_root_node(fresh_tree));
        assert(incremental_sexp != nullptr && fresh_sexp != nullptr);
        assert(std::string(incremental_sexp) == std::string(fresh_sexp));
        std::free(incremental_sexp);
        std::free(fresh_sexp);
        ts_tree_delete(fresh_tree);
        previous = current;
    }
    ts_tree_delete(incremental_tree);
    ts_parser_delete(fresh_parser);
    ts_parser_delete(incremental_parser);
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

    expect_language(
        tree_sitter_python(),
        "def main():\n    return 42\n",
        "def main(:\n    return 42\n");

    expect_language(
        tree_sitter_kotlin(),
        "fun main() { val value = 42 }",
        "fun main( { val value = 42 }");

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

    expect_query(tree_sitter_python(),
        "(identifier) @variable (function_definition name: (identifier) @function) "
        "(class_definition name: (identifier) @type) (string) @string "
        "(comment) @comment [(integer) (float)] @number "
        "[ (true) (false) (none) ] @constant "
        "\"def\" @keyword \"return\" @keyword",
        "def main():\n    return 42\n",
        "variable,function,keyword,number");

    expect_query(tree_sitter_kotlin(),
        "(simple_identifier) @variable (type_identifier) @type "
        "(function_declaration . (simple_identifier) @function) "
        "(string_literal) @string (character_literal) @string "
        "(line_comment) @comment (multiline_comment) @comment "
        "[(integer_literal) (long_literal) (hex_literal) (bin_literal) "
        "(unsigned_literal) (real_literal)] @number "
        "[ (boolean_literal) (null_literal) ] @constant "
        "\"fun\" @keyword \"val\" @keyword \"return\" @keyword",
        "fun main() { val value = 42 }",
        "variable,function,keyword,number");


    expect_incremental_equivalent(tree_sitter_python(), {
        "def main():\n    return 42\n",
        "def main():\n    value = 7\n    return value\n",
        "def main():\n    value = 7\n    return value + 1\n",
        "def main():\n    return 8\n"
    });
    expect_incremental_equivalent(tree_sitter_rust(), {
        "fn main() { let value = 1; }",
        "fn main() { let value = 2; }",
        "fn main() {\n    let café = 2;\n    println!(\"{}\", café);\n}",
        "fn main() {\n    let café = 20;\n    println!(\"{}\", café);\n}",
        "fn main() {\n    println!(\"done\");\n}"
    });
    expect_incremental_equivalent(tree_sitter_kotlin(), {
        "fun main() { val value = 1 }",
        "fun main() { val value = 12 }",
        "fun main() {\n    val label = \"編輯器\"\n    println(label)\n}",
        "fun main() {\n    println(\"ready\")\n}"
    });

    return 0;
}
