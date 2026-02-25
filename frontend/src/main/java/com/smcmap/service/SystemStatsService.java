package com.smcmap.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smcmap.model.SystemSnapshot;
import com.smcmap.model.ProcessSnapshot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SystemStatsService {

  private final HttpClient client;
  private final Gson gson;
  private final String backendUrl;

  public SystemStatsService(String backendUrl) {
    this.backendUrl = backendUrl;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();
    this.gson = new Gson();
  }

  public SystemDataResult fetchStats() throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(backendUrl + "/api/stats"))
        .header("Accept", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);

      SystemSnapshot sysSnap = new SystemSnapshot();
      JsonObject memObj = jsonResponse.getAsJsonObject("memory");
      sysSnap.setTotalRam(memObj.get("totalRam").getAsLong());
      sysSnap.setUsedRam(memObj.get("usedRam").getAsLong());
      sysSnap.setFreeRam(memObj.get("freeRam").getAsLong());

      List<ProcessSnapshot> processes = new ArrayList<>();
      jsonResponse.getAsJsonArray("processes").forEach(element -> {
        JsonObject procObj = element.getAsJsonObject();
        ProcessSnapshot ps = new ProcessSnapshot();
        ps.setPid(procObj.get("pid").getAsInt());
        ps.setName(procObj.get("name").getAsString());
        ps.setMemoryUsed(procObj.get("memoryUsed").getAsLong());
        processes.add(ps);
      });

      return new SystemDataResult(sysSnap, processes, jsonResponse.getAsJsonObject("cache"));
    } else {
      throw new RuntimeException("Failed to fetch stats: HTTP " + response.statusCode());
    }
  }

  public static class SystemDataResult {
    public final SystemSnapshot systemSnapshot;
    public final List<ProcessSnapshot> processes;
    public final JsonObject cacheData;

    public SystemDataResult(SystemSnapshot systemSnapshot, List<ProcessSnapshot> processes, JsonObject cacheData) {
      this.systemSnapshot = systemSnapshot;
      this.processes = processes;
      this.cacheData = cacheData;
    }
  }

  public String killProcess(int pid) throws Exception {
    String jsonBody = "{\"pid\":" + pid + "}";
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(backendUrl + "/api/process/kill"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
    return result.get("message").getAsString();
  }

  public String updateCacheConfig(long size, long blockSize, String policy) throws Exception {
    String jsonBody = "{\"size\":" + size + ",\"blockSize\":" + blockSize + ",\"policy\":\"" + policy + "\"}";
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(backendUrl + "/api/cache/config"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    JsonObject result = gson.fromJson(response.body(), JsonObject.class);
    return result.get("status").getAsString();
  }

  public String simulateCacheAccesses(int count) throws Exception {
    StringBuilder sb = new StringBuilder("{\"accesses\":[");
    java.util.Random rand = new java.util.Random();
    for (int i = 0; i < count; i++) {
      if (i > 0)
        sb.append(",");
      sb.append(rand.nextInt(4096) * 64); // random block-aligned addresses
    }
    sb.append("]}");

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(backendUrl + "/api/cache/simulate"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }

  public long measureLatency() {
    try {
      long start = System.nanoTime();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(backendUrl + "/api/stats"))
          .GET().build();
      client.send(request, HttpResponse.BodyHandlers.ofString());
      return (System.nanoTime() - start) / 1_000_000;
    } catch (Exception e) {
      return -1;
    }
  }
}
