#include "nanoce/filesystem.hpp"
#include "nanoce/nanoce.hpp"
#include <cassert>
#include <chrono>
#include <filesystem>
#include <string>

class TempDir {
public:
    TempDir() {
        const auto n = std::chrono::steady_clock::now().time_since_epoch().count();
        path = std::filesystem::temp_directory_path() / ("nanoce-test-" + std::to_string(n));
        std::filesystem::create_directories(path);
    }
    ~TempDir() { std::error_code ec; std::filesystem::remove_all(path, ec); }
    std::filesystem::path path;
};

int main() {
    assert(nanoce::version() == "0.1.0-dev");
    TempDir temp;
    const auto dir = temp.path / "src";
    const auto file = dir / "main.cpp";
    const auto copy = dir / "copy.cpp";
    assert(!nanoce::fs::create_directory(dir));
    assert(nanoce::fs::create_directory(dir) == std::errc::file_exists);
    assert(!nanoce::fs::create_file(file));
    assert(nanoce::fs::create_file(file) == std::errc::file_exists);
    const std::string code = "int main() { return 0; }\n";
    assert(!nanoce::fs::write_file(file, code));
    const auto read = nanoce::fs::read_file(file);
    assert(read && read.value == code);
    const auto info = nanoce::fs::status(file);
    assert(info && info.value.kind == nanoce::fs::EntryKind::regular_file);
    assert(info.value.size == code.size());
    const auto listing = nanoce::fs::list_directory(dir);
    assert(listing && listing.value.size() == 1);
    assert(listing.value.front().name == "main.cpp");
    assert(!nanoce::fs::copy_file(file, copy));
    assert(nanoce::fs::copy_file(file, copy) == std::errc::file_exists);
    assert(!nanoce::fs::rename(copy, dir / "renamed.cpp"));
    assert(nanoce::fs::status(dir / "renamed.cpp"));
    assert(!nanoce::fs::remove(dir / "renamed.cpp"));
    assert(!nanoce::fs::remove(file));
    assert(!nanoce::fs::remove(dir));
    assert(nanoce::fs::remove(dir) == std::errc::no_such_file_or_directory);
    const auto normalized = nanoce::fs::normalize_path(temp.path / "." / "src");
    assert(normalized && normalized.value == dir);
    return 0;
}
