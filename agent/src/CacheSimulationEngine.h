#ifndef CACHE_SIMULATION_ENGINE_H
#define CACHE_SIMULATION_ENGINE_H

#include <string>
#include <unordered_map>
#include <list>
#include <vector>

struct CacheSimulationResult {
    uint32_t hits;
    uint32_t misses;
    double hitRatio;
    std::string algorithm;
};

class CacheSimulationEngine {
public:
    CacheSimulationEngine(size_t cacheSize, size_t blockSize, const std::string& policy);
    
    void access(uint64_t address);
    CacheSimulationResult getResult() const;

private:
    size_t cacheSize;
    size_t blockSize;
    size_t numBlocks;
    std::string policy;
    
    uint32_t hits;
    uint32_t misses;

    // Data structures for tracking
    std::unordered_map<uint64_t, std::list<uint64_t>::iterator> cacheMap;
    std::list<uint64_t> cacheList; // For FIFO and LRU
    
    // For LFU
    std::unordered_map<uint64_t, uint32_t> frequencies;

    void accessFIFO(uint64_t blockAddress);
    void accessLRU(uint64_t blockAddress);
    void accessLFU(uint64_t blockAddress);
};

#endif // CACHE_SIMULATION_ENGINE_H
