cmake_minimum_required(VERSION 3.22.1)
project(Nanoce LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
option(NANOCE_BUILD_TESTS "Build Nanoce native tests" ON)

if(ANDROID)
    include(FetchContent)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build Tree-sitter as a static library" FORCE)
    set(TREE_SITTER_FEATURE_WASM OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(
        tree_sitter
        GIT_REPOSITORY https://github.com/tree-sitter/tree-sitter.git
        GIT_TAG v0.25.10
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(tree_sitter)
endif()

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
    target_link_libraries(nanoce_android PRIVATE nanoce_core tree-sitter)
    target_compile_features(nanoce_android PRIVATE cxx_std_20)
endif()

if(NANOCE_BUILD_TESTS AND NOT ANDROID)
    enable_testing()
    add_executable(nanoce_core_tests native/tests/core_tests.cpp)
    target_link_libraries(nanoce_core_tests PRIVATE nanoce_core)
    add_test(NAME nanoce_core_tests COMMAND nanoce_core_tests)
endif()
