#ifndef PROCESS_MEMORY_TRACKER_H
#define PROCESS_MEMORY_TRACKER_H

#include <vector>
#include <string>
#include <cstdint>

struct ProcessStats {
    uint32_t pid;
    std::string name;
    uint64_t memoryUsed; // in bytes
};

class ProcessMemoryTracker {
public:
    static std::vector<ProcessStats> getTopProcesses(int limit = 10);
};

#endif // PROCESS_MEMORY_TRACKER_H
