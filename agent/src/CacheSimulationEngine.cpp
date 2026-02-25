#include "CacheSimulationEngine.h"
#include <algorithm>

CacheSimulationEngine::CacheSimulationEngine(size_t cacheSize, size_t blockSize, const std::string& policy)
    : cacheSize(cacheSize), blockSize(blockSize), policy(policy), hits(0), misses(0) {
    numBlocks = cacheSize / blockSize;
    if (numBlocks == 0) numBlocks = 1;
}

void CacheSimulationEngine::access(uint64_t address) {
    uint64_t blockAddress = address / blockSize;

    if (policy == "FIFO") {
        accessFIFO(blockAddress);
    } else if (policy == "LRU") {
        accessLRU(blockAddress);
    } else if (policy == "LFU") {
        accessLFU(blockAddress);
    } else {
        // Default to LRU if unknown
        accessLRU(blockAddress);
    }
}

void CacheSimulationEngine::accessFIFO(uint64_t blockAddress) {
    if (cacheMap.find(blockAddress) != cacheMap.end()) {
        hits++;
    } else {
        misses++;
        if (cacheList.size() >= numBlocks) {
            uint64_t oldest = cacheList.front();
            cacheList.pop_front();
            cacheMap.erase(oldest);
        }
        cacheList.push_back(blockAddress);
        cacheMap[blockAddress] = --cacheList.end();
    }
}

void CacheSimulationEngine::accessLRU(uint64_t blockAddress) {
    auto it = cacheMap.find(blockAddress);
    if (it != cacheMap.end()) {
        hits++;
        cacheList.erase(it->second);
        cacheList.push_back(blockAddress);
        cacheMap[blockAddress] = --cacheList.end();
    } else {
        misses++;
        if (cacheList.size() >= numBlocks) {
            uint64_t leastRecentlyUsed = cacheList.front();
            cacheList.pop_front();
            cacheMap.erase(leastRecentlyUsed);
        }
        cacheList.push_back(blockAddress);
        cacheMap[blockAddress] = --cacheList.end();
    }
}

void CacheSimulationEngine::accessLFU(uint64_t blockAddress) {
    auto it = frequencies.find(blockAddress);
    if (it != frequencies.end()) {
        hits++;
        frequencies[blockAddress]++;
    } else {
        misses++;
        if (frequencies.size() >= numBlocks) {
            // Find LFU
            auto lfuIt = frequencies.begin();
            for (auto iter = frequencies.begin(); iter != frequencies.end(); ++iter) {
                if (iter->second < lfuIt->second) {
                    lfuIt = iter;
                }
            }
            frequencies.erase(lfuIt);
        }
        frequencies[blockAddress] = 1;
    }
}

CacheSimulationResult CacheSimulationEngine::getResult() const {
    CacheSimulationResult result;
    result.hits = hits;
    result.misses = misses;
    result.hitRatio = (hits + misses) == 0 ? 0.0 : (double)hits / (hits + misses);
    result.algorithm = policy;
    return result;
}
