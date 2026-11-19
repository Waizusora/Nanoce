#pragma once
#include <cstdint>
#include <filesystem>
#include <system_error>
#include <string>
#include <vector>

namespace nanoce::fs {
enum class EntryKind { regular_file, directory, symlink, other };

struct DirectoryEntry {
    std::string name;
    std::filesystem::path path;
    EntryKind kind{EntryKind::other};
    std::uintmax_t size{0};
};

template <typename T>
struct Result {
    T value{};
    std::error_code error{};
    [[nodiscard]] explicit operator bool() const noexcept { return !error; }
};

struct Status {
    EntryKind kind{EntryKind::other};
    std::uintmax_t size{0};
};

[[nodiscard]] Result<std::filesystem::path> normalize_path(const std::filesystem::path& path);
[[nodiscard]] Result<Status> status(const std::filesystem::path& path);
[[nodiscard]] Result<std::vector<DirectoryEntry>> list_directory(const std::filesystem::path& directory);
[[nodiscard]] std::error_code create_directory(const std::filesystem::path& directory);
[[nodiscard]] std::error_code create_file(const std::filesystem::path& file);
[[nodiscard]] Result<std::string> read_file(const std::filesystem::path& file);
[[nodiscard]] std::error_code write_file(const std::filesystem::path& file, const std::string& contents);
[[nodiscard]] std::error_code remove(const std::filesystem::path& path, bool recursive = false);
[[nodiscard]] std::error_code rename(const std::filesystem::path& from, const std::filesystem::path& to);
[[nodiscard]] std::error_code copy_file(const std::filesystem::path& from, const std::filesystem::path& to, bool overwrite = false);
} // namespace nanoce::fs
