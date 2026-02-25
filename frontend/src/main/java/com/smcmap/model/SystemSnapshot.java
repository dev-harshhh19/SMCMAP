package com.smcmap.model;

public class SystemSnapshot {
    private long totalRam;
    private long usedRam;
    private long freeRam;

    // Getters and setters
    public long getTotalRam() { return totalRam; }
    public void setTotalRam(long totalRam) { this.totalRam = totalRam; }

    public long getUsedRam() { return usedRam; }
    public void setUsedRam(long usedRam) { this.usedRam = usedRam; }

    public long getFreeRam() { return freeRam; }
    public void setFreeRam(long freeRam) { this.freeRam = freeRam; }
}
