#include "nanoce/filesystem.hpp"
#include <algorithm>
#include <fstream>
#include <iterator>

namespace nanoce::fs {
namespace {
EntryKind classify(const std::filesystem::file_status& value) {
    if (std::filesystem::is_symlink(value)) return EntryKind::symlink;
    if (std::filesystem::is_regular_file(value)) return EntryKind::regular_file;
    if (std::filesystem::is_directory(value)) return EntryKind::directory;
    return EntryKind::other;
}
bool entry_less(const DirectoryEntry& a, const DirectoryEntry& b) {
    const bool ad = a.kind == EntryKind::directory;
    const bool bd = b.kind == EntryKind::directory;
    if (ad != bd) return ad;
    return a.name < b.name;
}
}

Result<std::filesystem::path> normalize_path(const std::filesystem::path& path) {
    Result<std::filesystem::path> out;
    if (path.empty()) {
        out.error = std::make_error_code(std::errc::invalid_argument);
        return out;
    }
    out.value = std::filesystem::weakly_canonical(path, out.error);
    return out;
}

Result<Status> status(const std::filesystem::path& path) {
    Result<Status> out;
    const auto st = std::filesystem::symlink_status(path, out.error);
    if (out.error) return out;
    if (!std::filesystem::exists(st)) {
        out.error = std::make_error_code(std::errc::no_such_file_or_directory);
        return out;
    }
    out.value.kind = classify(st);
    if (out.value.kind == EntryKind::regular_file)
        out.value.size = std::filesystem::file_size(path, out.error);
    return out;
}

Result<std::vector<DirectoryEntry>> list_directory(const std::filesystem::path& directory) {
    Result<std::vector<DirectoryEntry>> out;
    std::filesystem::directory_iterator it(
        directory, std::filesystem::directory_options::skip_permission_denied, out.error);
    if (out.error) return out;
    const std::filesystem::directory_iterator end;
    while (it != end) {
        const auto p = it->path();
        std::error_code ec;
        const auto st = it->symlink_status(ec);
        if (!ec) {
            DirectoryEntry item;
            item.name = p.filename().string();
            item.path = p;
            item.kind = classify(st);
            if (item.kind == EntryKind::regular_file) {
                item.size = it->file_size(ec);
                if (ec) item.size = 0;
            }
            out.value.push_back(std::move(item));
        }
        it.increment(out.error);
        if (out.error) {
            out.value.clear();
            return out;
        }
    }
    std::sort(out.value.begin(), out.value.end(), entry_less);
    return out;
}

std::error_code create_directory(const std::filesystem::path& path) {
    std::error_code ec;
    const bool created = std::filesystem::create_directory(path, ec);
    if (!ec && !created) ec = std::make_error_code(std::errc::file_exists);
    return ec;
}

std::error_code create_file(const std::filesystem::path& path) {
    std::error_code ec;
    if (std::filesystem::exists(path, ec))
        return std::make_error_code(std::errc::file_exists);
    if (ec) return ec;
    std::ofstream stream(path, std::ios::binary | std::ios::out);
    if (!stream) return std::make_error_code(std::errc::io_error);
    stream.close();
    return stream ? std::error_code{} : std::make_error_code(std::errc::io_error);
}

Result<std::string> read_file(const std::filesystem::path& path) {
    Result<std::string> out;
    const auto st = std::filesystem::symlink_status(path, out.error);
    if (out.error) return out;
    if (!std::filesystem::is_regular_file(st)) {
        out.error = std::make_error_code(std::errc::invalid_argument);
        return out;
    }
    std::ifstream stream(path, std::ios::binary);
    if (!stream) {
        out.error = std::make_error_code(std::errc::io_error);
        return out;
    }
    out.value.assign(std::istreambuf_iterator<char>(stream), std::istreambuf_iterator<char>());
    if (stream.bad()) {
        out.value.clear();
        out.error = std::make_error_code(std::errc::io_error);
    }
    return out;
}

std::error_code write_file(const std::filesystem::path& path, const std::string& contents) {
    std::ofstream stream(path, std::ios::binary | std::ios::out | std::ios::trunc);
    if (!stream) return std::make_error_code(std::errc::io_error);
    stream.write(contents.data(), static_cast<std::streamsize>(contents.size()));
    stream.close();
    return stream ? std::error_code{} : std::make_error_code(std::errc::io_error);
}

std::error_code remove(const std::filesystem::path& path, bool recursive) {
    std::error_code ec;
    if (recursive) {
        std::filesystem::remove_all(path, ec);
    } else {
        const bool removed = std::filesystem::remove(path, ec);
        if (!ec && !removed) ec = std::make_error_code(std::errc::no_such_file_or_directory);
    }
    return ec;
}

std::error_code rename(const std::filesystem::path& from, const std::filesystem::path& to) {
    std::error_code ec;
    std::filesystem::rename(from, to, ec);
    return ec;
}

std::error_code copy_file(const std::filesystem::path& from, const std::filesystem::path& to, bool overwrite) {
    std::error_code ec;
    const auto options = overwrite ? std::filesystem::copy_options::overwrite_existing
                                   : std::filesystem::copy_options::none;
    std::filesystem::copy_file(from, to, options, ec);
    return ec;
}
} // namespace nanoce::fs
