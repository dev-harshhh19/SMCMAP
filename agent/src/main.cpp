#include "httplib.h"
#include <nlohmann/json.hpp>
#include <iostream>
#include <windows.h>
#include "MemoryStatsReader.h"
#include "ProcessMemoryTracker.h"
#include "CacheSimulationEngine.h"

using json = nlohmann::json;

int main(int argc, char** argv) {
    httplib::Server svr;

    // Default simulation engine (global for simplicity)
    CacheSimulationEngine cacheSim(1024 * 1024, 64, "LRU"); // 1MB cache, 64B blocks

    svr.Get("/api/stats", [&](const httplib::Request& req, httplib::Response& res) {
        // Prepare memory stats
        MemoryStats memStats = MemoryStatsReader::getMemoryStats();
        
        std::vector<ProcessStats> processes = ProcessMemoryTracker::getTopProcesses(20);
        json jsonProcesses = json::array();
        for (const auto& proc : processes) {
            jsonProcesses.push_back({
                {"pid", proc.pid},
                {"name", proc.name},
                {"memoryUsed", proc.memoryUsed}
            });
        }

        // Get cache engine stats
        CacheSimulationResult cacheResult = cacheSim.getResult();
        
        // Build aggregate JSON
        json response = {
            {"memory", {
                {"totalRam", memStats.totalRam},
                {"usedRam", memStats.usedRam},
                {"freeRam", memStats.freeRam}
            }},
            {"processes", jsonProcesses},
            {"cache", {
                {"algorithm", cacheResult.algorithm},
                {"hits", cacheResult.hits},
                {"misses", cacheResult.misses},
                {"hitRatio", cacheResult.hitRatio}
            }}
        };

        res.set_content(response.dump(), "application/json");
        // Add CORS to allow java frontend to fetch easily if needed later through web
        res.set_header("Access-Control-Allow-Origin", "*");
    });

    svr.Post("/api/cache/config", [&](const httplib::Request& req, httplib::Response& res) {
        try {
            auto config = json::parse(req.body);
            size_t size = config.value("size", 1024 * 1024);
            size_t blockSize = config.value("blockSize", 64);
            std::string policy = config.value("policy", "LRU");

            cacheSim = CacheSimulationEngine(size, blockSize, policy);

            res.set_content(R"({"status":"success"})", "application/json");
        } catch (const std::exception& e) {
            res.status = 400;
            res.set_content(R"({"status":"error","message":"Invalid JSON"})", "application/json");
        }
    });

    // Dummy endpoint to simulate memory accesses
    svr.Post("/api/cache/simulate", [&](const httplib::Request& req, httplib::Response& res) {
         try {
            auto config = json::parse(req.body);
            std::vector<uint64_t> accesses = config.value("accesses", std::vector<uint64_t>{});
            for(uint64_t addr : accesses) {
                cacheSim.access(addr);
            }
            CacheSimulationResult cacheResult = cacheSim.getResult();
            json response = {
                {"algorithm", cacheResult.algorithm},
                {"hits", cacheResult.hits},
                {"misses", cacheResult.misses},
                {"hitRatio", cacheResult.hitRatio}
            };
            res.set_content(response.dump(), "application/json");
         } catch (...) {
            res.status = 400;
            res.set_content(R"({"status":"error"})", "application/json");
         }
    });

    // Kill a process by PID
    svr.Post("/api/process/kill", [&](const httplib::Request& req, httplib::Response& res) {
        res.set_header("Access-Control-Allow-Origin", "*");
        try {
            auto body = json::parse(req.body);
            int pid = body.value("pid", 0);
            if (pid == 0) {
                res.status = 400;
                res.set_content(R"({"status":"error","message":"Missing PID"})", "application/json");
                return;
            }

            HANDLE hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, pid);
            if (hProcess == NULL) {
                res.status = 403;
                res.set_content(R"({"status":"error","message":"Cannot open process. Access denied or invalid PID."})", "application/json");
                return;
            }

            BOOL result = TerminateProcess(hProcess, 1);
            CloseHandle(hProcess);

            if (result) {
                json resp = {{"status", "success"}, {"message", "Process terminated"}, {"pid", pid}};
                res.set_content(resp.dump(), "application/json");
            } else {
                res.status = 500;
                res.set_content(R"({"status":"error","message":"Failed to terminate process"})", "application/json");
            }
        } catch (...) {
            res.status = 400;
            res.set_content(R"({"status":"error","message":"Invalid request"})", "application/json");
        }
    });

    int port = 8080;
    std::cout << "Starting C++ System Agent on port " << port << "..." << std::endl;
    svr.listen("0.0.0.0", port);
    
    return 0;
}
