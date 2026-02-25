#include "MemoryStatsReader.h"
#include <windows.h>

MemoryStats MemoryStatsReader::getMemoryStats() {
    MEMORYSTATUSEX memInfo;
    memInfo.dwLength = sizeof(MEMORYSTATUSEX);
    GlobalMemoryStatusEx(&memInfo);

    MemoryStats stats;
    stats.totalRam = memInfo.ullTotalPhys;
    stats.freeRam = memInfo.ullAvailPhys;
    stats.usedRam = stats.totalRam - stats.freeRam;

    return stats;
}
