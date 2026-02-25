package com.smcmap.ui;

import com.smcmap.service.AnalysisPersistenceService;
import com.smcmap.service.SystemStatsService;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Java bridge exposed to JavaScript in the WebView.
 * Methods called from JS via window.javaBridge.methodName()
 */
public class JavaBridge {

  private final SystemStatsService statsService;
  private final AnalysisPersistenceService persistenceService;
  private final WebEngine webEngine;
  private final long startTimeMs = System.currentTimeMillis();

  public JavaBridge(SystemStatsService statsService, AnalysisPersistenceService persistenceService,
      WebEngine webEngine) {
    this.statsService = statsService;
    this.persistenceService = persistenceService;
    this.webEngine = webEngine;
  }

  /** Return real system info: OS, kernel, hostname, arch, java, cpu cores. */
  public void loadSystemInfo() {
    String osName = System.getProperty("os.name", "Unknown");
    String osVersion = System.getProperty("os.version", "0");
    String arch = System.getProperty("os.arch", "x64");
    String javaVersion = System.getProperty("java.version", "17");
    int cpuCores = Runtime.getRuntime().availableProcessors();
    String hostname;
    try {
      hostname = java.net.InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      hostname = "localhost";
    }
    String userName = System.getProperty("user.name", "admin");

    String json = "{\"osName\":\"" + esc(osName) + "\""
        + ",\"kernel\":\"" + esc(osVersion) + "\""
        + ",\"arch\":\"" + esc(arch) + "\""
        + ",\"hostname\":\"" + esc(hostname) + "\""
        + ",\"javaVersion\":\"" + esc(javaVersion) + "\""
        + ",\"cpuCores\":" + cpuCores
        + ",\"userName\":\"" + esc(userName) + "\""
        + "}";
    Platform.runLater(() -> {
      webEngine.executeScript("onSystemInfo('" + escJs(json) + "')");
    });
  }

  /** Kill a process by PID. Called from JS. */
  public void killProcess(int pid, String name) {
    System.out.println("[SMCMAP] Kill request: PID=" + pid + " Name=" + name);
    new Thread(() -> {
      try {
        String msg = statsService.killProcess(pid);
        Platform.runLater(() -> {
          webEngine.executeScript(
              "document.getElementById('proc-status').textContent='KILLED PID " + pid + ": " + esc(msg) + "';");
          refresh();
        });
      } catch (Exception e) {
        Platform.runLater(() -> {
          webEngine.executeScript(
              "document.getElementById('proc-status').textContent='KILL FAILED: " + esc(e.getMessage()) + "';");
        });
      }
    }).start();
  }

  /** Manual refresh trigger from JS. */
  public void refresh() {
    new Thread(() -> {
      try {
        SystemStatsService.SystemDataResult result = statsService.fetchStats();
        persistenceService.saveSystemSnapshot(result.systemSnapshot);
        Platform.runLater(() -> {
          try {
            String json = buildJson(result);
            webEngine.executeScript("updateDashboard('" + escJs(json) + "')");
          } catch (Exception ex) {
            System.err.println("[SMCMAP] Refresh JS error: " + ex.getMessage());
          }
        });
      } catch (Exception e) {
        System.err.println("[SMCMAP] Refresh failed: " + e.getMessage());
      }
    }).start();
  }

  /** Fetch analytics history with timestamps and send to JS. */
  public void loadAnalytics() {
    new Thread(() -> {
      List<String[]> history = persistenceService.getHistoricalSnapshotsWithTime(100);
      int count = persistenceService.getSnapshotCount();
      long dbSize = persistenceService.getDbSizeKB();

      StringBuilder sb = new StringBuilder("{\"count\":").append(count);
      sb.append(",\"dbSizeKB\":").append(dbSize);
      sb.append(",\"records\":[");
      for (int i = 0; i < history.size(); i++) {
        String[] row = history.get(i);
        if (i > 0)
          sb.append(",");
        sb.append("{\"totalRam\":").append(row[0]);
        sb.append(",\"usedRam\":").append(row[1]);
        sb.append(",\"freeRam\":").append(row[2]);
        sb.append(",\"timestamp\":\"").append(row[3] != null ? row[3] : "N/A").append("\"}");
      }
      sb.append("]}");
      String json = sb.toString();
      Platform.runLater(() -> {
        webEngine.executeScript("updateAnalytics('" + escJs(json) + "')");
      });
    }).start();
  }

  /** Load node info: latency, uptime, connection details. */
  public void loadNodeInfo() {
    new Thread(() -> {
      long latency = statsService.measureLatency();
      long uptimeSec = (System.currentTimeMillis() - startTimeMs) / 1000;
      int snapshots = persistenceService.getSnapshotCount();
      long dbSize = persistenceService.getDbSizeKB();

      String json = "{\"latencyMs\":" + latency
          + ",\"uptimeSec\":" + uptimeSec
          + ",\"totalSnapshots\":" + snapshots
          + ",\"dbSizeKB\":" + dbSize
          + ",\"agentUrl\":\"http://127.0.0.1:8080\""
          + ",\"status\":\"" + (latency >= 0 ? "CONNECTED" : "DISCONNECTED") + "\""
          + "}";
      Platform.runLater(() -> {
        webEngine.executeScript("updateNodeInfo('" + escJs(json) + "')");
      });
    }).start();
  }

  /** Update cache config on the C++ agent. */
  public void updateCacheConfig(int sizeKB, int blockSize, String policy) {
    new Thread(() -> {
      try {
        String result = statsService.updateCacheConfig(sizeKB * 1024L, blockSize, policy);
        Platform.runLater(() -> {
          webEngine.executeScript("onConfigResult('" + esc(result) + "')");
        });
      } catch (Exception e) {
        Platform.runLater(() -> {
          webEngine.executeScript("onConfigResult('error: " + esc(e.getMessage()) + "')");
        });
      }
    }).start();
  }

  /** Run cache simulation with N random accesses. */
  public void runCacheSimulation(int count) {
    new Thread(() -> {
      try {
        String result = statsService.simulateCacheAccesses(count);
        Platform.runLater(() -> {
          webEngine.executeScript("onSimulationResult('" + escJs(result) + "')");
        });
      } catch (Exception e) {
        Platform.runLater(() -> {
          webEngine.executeScript("onSimulationResult('{\"error\":\"" + esc(e.getMessage()) + "\"}')");
        });
      }
    }).start();
  }

  public void exportStats(String format) {
    new Thread(() -> {
      try {
        List<String[]> snapshots = persistenceService.getSnapshotsLastMinutes(3);
        if (snapshots.isEmpty()) {
          System.out.println("[SMCMAP] No data available for the last 3 minutes to export.");
          return;
        }

        Platform.runLater(() -> {
          FileChooser fileChooser = new FileChooser();
          fileChooser.setTitle("Save Stats");
          fileChooser.setInitialFileName("smcmap_stats_" + System.currentTimeMillis());

          if ("json".equalsIgnoreCase(format)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
          } else if ("md".equalsIgnoreCase(format)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown Files", "*.md"));
          } else if ("pdf".equalsIgnoreCase(format)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents", "*.pdf"));
          }

          File file = fileChooser.showSaveDialog(null);
          if (file != null) {
            new Thread(() -> saveToFile(file, format, snapshots)).start();
          }
        });

      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
  }

  private void saveToFile(File file, String format, List<String[]> snapshots) {
    try {
      if ("json".equalsIgnoreCase(format)) {
        JsonArray array = new JsonArray();
        for (String[] snap : snapshots) {
          JsonObject obj = new JsonObject();
          obj.addProperty("totalRamMB", Long.parseLong(snap[0]));
          obj.addProperty("usedRamMB", Long.parseLong(snap[1]));
          obj.addProperty("freeRamMB", Long.parseLong(snap[2]));
          obj.addProperty("timestamp", snap[3]);
          array.add(obj);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
          writer.write(gson.toJson(array));
        }
      } else if ("md".equalsIgnoreCase(format)) {
        try (FileWriter writer = new FileWriter(file)) {
          writer.write("# SMCMAP System Statistics (Last 3 Minutes)\n\n");
          writer.write("| Timestamp | Total RAM (MB) | Used RAM (MB) | Free RAM (MB) |\n");
          writer.write("|---|---|---|---|\n");
          for (String[] snap : snapshots) {
            writer.write(String.format("| %s | %s | %s | %s |\n", snap[3], snap[0], snap[1], snap[2]));
          }
        }
      } else if ("pdf".equalsIgnoreCase(format)) {
        try (PDDocument document = new PDDocument()) {
          PDPage page = new PDPage();
          document.addPage(page);

          try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream
                .setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
            contentStream.setLeading(14.5f);
            contentStream.newLineAtOffset(25, 750);

            contentStream.showText("SMCMAP System Statistics (Last 3 Minutes)");
            contentStream.newLine();
            contentStream.newLine();

            contentStream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(Standard14Fonts.FontName.HELVETICA),
                10);
            contentStream.showText("Timestamp               | Total RAM (MB) | Used RAM (MB) | Free RAM (MB)");
            contentStream.newLine();
            contentStream
                .showText("-----------------------------------------------------------------------------------------");
            contentStream.newLine();

            for (String[] snap : snapshots) {
              String row = String.format("%-25s | %-14s | %-13s | %-13s", snap[3], snap[0], snap[1], snap[2]);
              contentStream.showText(row);
              contentStream.newLine();
            }
            contentStream.endText();
          }
          document.save(file);
        }
      }
      System.out.println("[SMCMAP] Successfully exported stats to " + file.getAbsolutePath());
    } catch (IOException e) {
      System.err.println("[SMCMAP] Error exporting stats: " + e.getMessage());
      e.printStackTrace();
    }
  }

  // --- JSON builders ---
  private String buildJson(SystemStatsService.SystemDataResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"memory\":{");
    sb.append("\"totalRam\":").append(result.systemSnapshot.getTotalRam()).append(",");
    sb.append("\"usedRam\":").append(result.systemSnapshot.getUsedRam()).append(",");
    sb.append("\"freeRam\":").append(result.systemSnapshot.getFreeRam());
    sb.append("},");
    sb.append("\"cache\":{");
    sb.append("\"algorithm\":\"").append(result.cacheData.get("algorithm").getAsString()).append("\",");
    sb.append("\"hits\":").append(result.cacheData.get("hits").getAsInt()).append(",");
    sb.append("\"misses\":").append(result.cacheData.get("misses").getAsInt()).append(",");
    sb.append("\"hitRatio\":").append(result.cacheData.get("hitRatio").getAsDouble());
    sb.append("},");
    sb.append("\"processes\":[");
    for (int i = 0; i < result.processes.size(); i++) {
      var p = result.processes.get(i);
      if (i > 0)
        sb.append(",");
      sb.append("{\"pid\":").append(p.getPid());
      sb.append(",\"name\":\"").append(p.getName().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
      sb.append(",\"memoryUsed\":").append(p.getMemoryUsed()).append("}");
    }
    sb.append("]}");
    return sb.toString();
  }

  private String escJs(String s) {
    return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
  }

  private String esc(String s) {
    if (s == null)
      return "null";
    return s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
  }
}
