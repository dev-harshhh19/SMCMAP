cmake_minimum_required(VERSION 3.14)
project(smcmap_agent CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Fetch cpp-httplib (header-only mode)
set(HTTPLIB_COMPILE OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    httplib
    GIT_REPOSITORY https://github.com/yhirose/cpp-httplib.git
    GIT_TAG        v0.14.1
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(httplib)

# Fetch nlohmann_json for JSON serialization
FetchContent_Declare(
    json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(json)

# Add source files
set(SOURCES
    src/main.cpp
    src/MemoryStatsReader.cpp
    src/ProcessMemoryTracker.cpp
    src/CacheSimulationEngine.cpp
)

# Output executable
add_executable(smcmap_agent ${SOURCES})

# Include directories
target_include_directories(smcmap_agent PRIVATE src)

# Link libraries
target_link_libraries(smcmap_agent PRIVATE
    httplib
    nlohmann_json::nlohmann_json
)

# Windows-specific system libraries
if(WIN32)
    target_link_libraries(smcmap_agent PRIVATE psapi wsock32 ws2_32)
endif()
