cmake_minimum_required(VERSION 3.22.1)
project(Nanoce LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
option(NANOCE_BUILD_TESTS "Build Nanoce native tests" ON)

include(FetchContent)
    FetchContent_Declare(
        tree_sitter
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter.git
        GIT_TAG v0.25.10
        GIT_SHALLOW TRUE
    )
    FetchContent_GetProperties(tree_sitter)
    if(NOT tree_sitter_POPULATED)
        FetchContent_Populate(tree_sitter)
    endif()

    add_library(tree-sitter STATIC
        ${tree_sitter_SOURCE_DIR}/lib/src/lib.c
    )
    target_include_directories(tree-sitter PUBLIC
        ${tree_sitter_SOURCE_DIR}/lib/include
    )
    set_target_properties(tree-sitter PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        C_STANDARD 11
        C_STANDARD_REQUIRED ON
    )

    FetchContent_Declare(
        tree_sitter_json
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-json.git
        GIT_TAG v0.24.8
        GIT_SHALLOW TRUE
    )
    FetchContent_GetProperties(tree_sitter_json)
    if(NOT tree_sitter_json_POPULATED)
        FetchContent_Populate(tree_sitter_json)
    endif()

    add_library(tree-sitter-json STATIC
        ${tree_sitter_json_SOURCE_DIR}/src/parser.c
    )
    target_include_directories(tree-sitter-json PRIVATE
        ${tree_sitter_json_SOURCE_DIR}/src
    )
    target_link_libraries(tree-sitter-json PUBLIC tree-sitter)
    set_target_properties(tree-sitter-json PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        C_STANDARD 11
        C_STANDARD_REQUIRED ON
    )

    FetchContent_Declare(
        tree_sitter_c
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-c.git
        GIT_TAG v0.24.2
        GIT_SHALLOW TRUE
    )
    FetchContent_GetProperties(tree_sitter_c)
    if(NOT tree_sitter_c_POPULATED)
        FetchContent_Populate(tree_sitter_c)
    endif()
    add_library(tree-sitter-c STATIC
        ${tree_sitter_c_SOURCE_DIR}/src/parser.c
    )
    target_include_directories(tree-sitter-c PRIVATE
        ${tree_sitter_c_SOURCE_DIR}/src
    )
    target_link_libraries(tree-sitter-c PUBLIC tree-sitter)
    set_target_properties(tree-sitter-c PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        C_STANDARD 11
        C_STANDARD_REQUIRED ON
    )

    FetchContent_Declare(
        tree_sitter_rust
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-rust.git
        GIT_TAG v0.24.2
        GIT_SHALLOW TRUE
    )
    FetchContent_GetProperties(tree_sitter_rust)
    if(NOT tree_sitter_rust_POPULATED)
        FetchContent_Populate(tree_sitter_rust)
    endif()
    add_library(tree-sitter-rust STATIC
        ${tree_sitter_rust_SOURCE_DIR}/src/parser.c
        ${tree_sitter_rust_SOURCE_DIR}/src/scanner.c
    )
    target_include_directories(tree-sitter-rust PRIVATE
        ${tree_sitter_rust_SOURCE_DIR}/src
    )
    target_link_libraries(tree-sitter-rust PUBLIC tree-sitter)
    set_target_properties(tree-sitter-rust PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        C_STANDARD 11
        C_STANDARD_REQUIRED ON
    )

    FetchContent_Declare(
        tree_sitter_cpp
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-cpp.git
        GIT_TAG v0.23.4
        GIT_SHALLOW TRUE
    )
    FetchContent_GetProperties(tree_sitter_cpp)
    if(NOT tree_sitter_cpp_POPULATED)
        FetchContent_Populate(tree_sitter_cpp)
    endif()
    add_library(tree-sitter-cpp STATIC
        ${tree_sitter_cpp_SOURCE_DIR}/src/parser.c
        ${tree_sitter_cpp_SOURCE_DIR}/src/scanner.c
    )
    target_include_directories(tree-sitter-cpp PRIVATE
        ${tree_sitter_cpp_SOURCE_DIR}/src
    )
    target_link_libraries(tree-sitter-cpp PUBLIC tree-sitter)
    set_target_properties(tree-sitter-cpp PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        C_STANDARD 11
        C_STANDARD_REQUIRED ON
    )


FetchContent_Declare(
    tree_sitter_python
    GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter-python.git
    GIT_TAG v0.25.0
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(tree_sitter_python)
if(NOT tree_sitter_python_POPULATED)
    FetchContent_Populate(tree_sitter_python)
endif()
add_library(tree-sitter-python STATIC
    ${tree_sitter_python_SOURCE_DIR}/src/parser.c
    ${tree_sitter_python_SOURCE_DIR}/src/scanner.c
)
target_include_directories(tree-sitter-python PRIVATE
    ${tree_sitter_python_SOURCE_DIR}/src
)
target_link_libraries(tree-sitter-python PUBLIC tree-sitter)
set_target_properties(tree-sitter-python PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
)

FetchContent_Declare(
    tree_sitter_kotlin
    GIT_REPOSITORY https://github.com/fwcd/tree-sitter-kotlin.git
    GIT_TAG d6af96d2e23ef0a8ff5ab512864378cf08fba311
    GIT_SHALLOW FALSE
)
FetchContent_GetProperties(tree_sitter_kotlin)
if(NOT tree_sitter_kotlin_POPULATED)
    FetchContent_Populate(tree_sitter_kotlin)
endif()
add_library(tree-sitter-kotlin STATIC
    ${tree_sitter_kotlin_SOURCE_DIR}/src/parser.c
    ${tree_sitter_kotlin_SOURCE_DIR}/src/scanner.c
)
target_include_directories(tree-sitter-kotlin PRIVATE
    ${tree_sitter_kotlin_SOURCE_DIR}/src
)
target_link_libraries(tree-sitter-kotlin PUBLIC tree-sitter)
set_target_properties(tree-sitter-kotlin PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
)

add_library(nanoce_core STATIC
    native/core/src/nanoce.cpp
    native/core/src/filesystem.cpp
)
set_target_properties(nanoce_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(nanoce_core PUBLIC native/core/include)
target_compile_features(nanoce_core PUBLIC cxx_std_20)

if(ANDROID)
    target_compile_definitions(nanoce_core PUBLIC NANOCE_PLATFORM_ANDROID=1)
    add_library(nanoce_android SHARED native/android/jni_bridge.cpp)
    target_link_libraries(nanoce_android PRIVATE nanoce_core tree-sitter tree-sitter-json tree-sitter-c tree-sitter-cpp tree-sitter-rust tree-sitter-python tree-sitter-kotlin)
    target_compile_features(nanoce_android PRIVATE cxx_std_20)
endif()

if(NANOCE_BUILD_TESTS AND NOT ANDROID)
    enable_testing()
    add_executable(nanoce_parser_tests native/tests/parser_tests.cpp)
    target_link_libraries(nanoce_parser_tests PRIVATE tree-sitter tree-sitter-json tree-sitter-c tree-sitter-cpp tree-sitter-rust)
    add_test(NAME nanoce_parser_tests COMMAND nanoce_parser_tests)
    add_executable(nanoce_core_tests native/tests/core_tests.cpp)
    target_link_libraries(nanoce_core_tests PRIVATE nanoce_core)
    add_test(NAME nanoce_core_tests COMMAND nanoce_core_tests)
endif()
