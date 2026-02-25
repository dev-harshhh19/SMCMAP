#ifndef MEMORY_STATS_READER_H
#define MEMORY_STATS_READER_H

#include <cstdint>

struct MemoryStats {
    uint64_t totalRam;
    uint64_t usedRam;
    uint64_t freeRam;
};

class MemoryStatsReader {
public:
    static MemoryStats getMemoryStats();
};

#endif // MEMORY_STATS_READER_H
