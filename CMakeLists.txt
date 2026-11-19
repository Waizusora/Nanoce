cmake_minimum_required(VERSION 3.22.1)

project(Nanoce LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(NANOCE_BUILD_TESTS "Build Nanoce native tests" ON)

add_library(nanoce_core STATIC
    native/core/src/nanoce.cpp
)

target_include_directories(nanoce_core
    PUBLIC
        native/core/include
)

target_compile_features(nanoce_core PUBLIC cxx_std_20)

if(ANDROID)
    target_compile_definitions(nanoce_core PUBLIC NANOCE_PLATFORM_ANDROID=1)
endif()

if(NANOCE_BUILD_TESTS AND NOT ANDROID)
    enable_testing()
    add_executable(nanoce_core_tests
        native/tests/core_tests.cpp
    )
    target_link_libraries(nanoce_core_tests PRIVATE nanoce_core)
    add_test(NAME nanoce_core_tests COMMAND nanoce_core_tests)
endif()
