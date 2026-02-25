#include "ProcessMemoryTracker.h"
#include <windows.h>
#include <tlhelp32.h>
#include <psapi.h>
#include <algorithm>

std::vector<ProcessStats> ProcessMemoryTracker::getTopProcesses(int limit) {
    std::vector<ProcessStats> processes;

    HANDLE hProcessSnap;
    PROCESSENTRY32 pe32;

    hProcessSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hProcessSnap == INVALID_HANDLE_VALUE) {
        return processes;
    }

    pe32.dwSize = sizeof(PROCESSENTRY32);

    if (!Process32First(hProcessSnap, &pe32)) {
        CloseHandle(hProcessSnap);
        return processes;
    }

    do {
        uint32_t pid = pe32.th32ProcessID;
        if (pid == 0) continue; // Skip System Idle Process

        HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, FALSE, pid);
        if (hProcess) {
            PROCESS_MEMORY_COUNTERS pmc;
            if (GetProcessMemoryInfo(hProcess, &pmc, sizeof(pmc))) {
                ProcessStats stats;
                stats.pid = pid;
                stats.name = pe32.szExeFile;
                stats.memoryUsed = pmc.WorkingSetSize;
                processes.push_back(stats);
            }
            CloseHandle(hProcess);
        }
    } while (Process32Next(hProcessSnap, &pe32));

    CloseHandle(hProcessSnap);

    // Sort by memory used descending
    std::sort(processes.begin(), processes.end(), [](const ProcessStats& a, const ProcessStats& b) {
        return a.memoryUsed > b.memoryUsed;
    });

    if (processes.size() > limit) {
        processes.resize(limit);
    }

    return processes;
}
