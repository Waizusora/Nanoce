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

    return 0;
}
