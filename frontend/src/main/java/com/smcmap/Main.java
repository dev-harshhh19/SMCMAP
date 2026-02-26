package com.smcmap;

import com.smcmap.service.SystemStatsService;
import com.smcmap.service.AnalysisPersistenceService;
import com.smcmap.ui.JavaBridge;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;

import java.io.File;
import java.net.URL;
import java.nio.file.*;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends Application {

  private Timer timer;
  private SystemStatsService statsService;
  private AnalysisPersistenceService persistenceService;
  private JavaBridge bridge;
  private String htmlFilePath; // filesystem path for hot-reload
  private Thread watchThread;

  private Process agentProcess;

  @Override
  public void start(Stage primaryStage) throws Exception {
    startAgentProcess();

    statsService = new SystemStatsService("http://127.0.0.1:8080");
    persistenceService = new AnalysisPersistenceService();

    WebView webView = new WebView();
    WebEngine webEngine = webView.getEngine();

    // Disable right-click context menu
    webView.setContextMenuEnabled(false);

    // Try loading from source directory first (hot-reload in dev)
    File srcHtml = findSourceHtml();
    if (srcHtml != null && srcHtml.exists()) {
      htmlFilePath = srcHtml.getAbsolutePath();
      System.out.println("[SMCMAP] HOT-RELOAD enabled: " + htmlFilePath);
      startFileWatcher(srcHtml, webEngine);
    } else {
      // Fallback to classpath resource
      URL htmlUrl = getClass().getResource("/web/dashboard.html");
      if (htmlUrl == null) {
        throw new RuntimeException("Cannot find web/dashboard.html");
      }
      htmlFilePath = htmlUrl.toExternalForm();
      System.out.println("[SMCMAP] Using classpath HTML (no hot-reload)");
    }

    // Create the Java bridge object
    bridge = new JavaBridge(statsService, persistenceService, webEngine);

    // When page loads, inject the Java bridge and start polling
    webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        JSObject window = (JSObject) webEngine.executeScript("window");
        window.setMember("javaBridge", bridge);
        System.out.println("[SMCMAP] Java-JS bridge injected.");
        bridge.loadSystemInfo();
        startPolling(webEngine);
      }
    });

    loadHtml(webEngine);

    StackPane root = new StackPane(webView);
    Scene scene = new Scene(root, 1280, 800);

    // F5 to manually reload the HTML
    scene.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.F5) {
        System.out.println("[SMCMAP] F5 - Reloading dashboard...");
        loadHtml(webEngine);
      }
    });

    primaryStage.setTitle("SMCMAP V2 | Modular Workspace");
    primaryStage.setScene(scene);
    primaryStage.setOnCloseRequest(e -> {
      if (timer != null)
        timer.cancel();
      if (watchThread != null)
        watchThread.interrupt();
      if (agentProcess != null) {
        agentProcess.destroy();
      }
      Platform.exit();
      System.exit(0);
    });
    primaryStage.show();
  }

  private void startAgentProcess() {
    new Thread(() -> {
      try {
        // Try finding it relative to where the Java app is actually running (important
        // for Start Menu shortcuts)
        File agentExe = new File("agent/build/Release/smcmap_agent.exe"); // Dev environment

        if (!agentExe.exists()) {
          // Check same directory (Portable / MSI Install folder)
          agentExe = new File(System.getProperty("user.dir"), "smcmap_agent.exe");
        }

        if (!agentExe.exists()) {
          // If launched via Start Menu, user.dir might be System32 or the user's home
          // dir.
          // Get the base path of the running Java executable directory instead.
          try {
            String jarPath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File libDir = new File(jarPath).getParentFile(); // e.g., C:\Program Files\SMCMAP\app\
            File appRoot = libDir.getParentFile(); // e.g., C:\Program Files\SMCMAP\
            agentExe = new File(appRoot, "smcmap_agent.exe");
          } catch (Exception ignored) {
          }
        }

        if (agentExe.exists()) {
          System.out.println("[SMCMAP] Starting bundled C++ Agent: " + agentExe.getAbsolutePath());
          ProcessBuilder pb = new ProcessBuilder(agentExe.getAbsolutePath());
          // Merge error and output
          pb.redirectErrorStream(true);
          // Redirect output to console
          pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
          agentProcess = pb.start();
        } else {
          System.out.println("[SMCMAP] Warning: smcmap_agent.exe not found at " + agentExe.getAbsolutePath()
              + ". Assuming it is running externally.");
        }
      } catch (Exception e) {
        System.err.println("[SMCMAP] Failed to start agent process: " + e.getMessage());
      }
    }).start();
  }

  private void startPolling(WebEngine webEngine) {
    timer = new Timer(true);
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        try {
          SystemStatsService.SystemDataResult result = statsService.fetchStats();
          // Persist to SQLite
          persistenceService.saveSystemSnapshot(result.systemSnapshot);

          // Build JSON to pass to JS
          String json = buildJson(result);

          Platform.runLater(() -> {
            try {
              webEngine.executeScript("updateDashboard('" + escapeJs(json) + "')");
            } catch (Exception ex) {
              System.err.println("[SMCMAP] JS update error: " + ex.getMessage());
            }
          });
        } catch (Exception e) {
          Platform.runLater(() -> {
            try {
              webEngine.executeScript("document.getElementById('proc-status').textContent='AGENT DISCONNECTED'");
            } catch (Exception ignored) {
            }
          });
        }
      }
    }, 0, 1000);
  }

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
      sb.append(",\"name\":\"").append(escapeJsonStr(p.getName())).append("\"");
      sb.append(",\"memoryUsed\":").append(p.getMemoryUsed()).append("}");
    }
    sb.append("]}");
    return sb.toString();
  }

  private String escapeJs(String s) {
    return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
  }

  private String escapeJsonStr(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  // Load (or reload) the HTML into WebView
  private void loadHtml(WebEngine webEngine) {
    if (htmlFilePath.startsWith("file:") || htmlFilePath.startsWith("jar:")) {
      webEngine.load(htmlFilePath);
    } else {
      webEngine.load(new File(htmlFilePath).toURI().toString());
    }
  }

  // Try to find the HTML in the source tree for dev hot-reload
  private File findSourceHtml() {
    // Walk up from CWD to find the source resource
    String[] candidates = {
        "frontend/src/main/resources/web/dashboard.html",
        "src/main/resources/web/dashboard.html",
    };
    for (String path : candidates) {
      File f = new File(path);
      if (f.exists())
        return f;
    }
    // Try from user.dir
    String userDir = System.getProperty("user.dir");
    for (String path : candidates) {
      File f = new File(userDir, path);
      if (f.exists())
        return f;
    }
    return null;
  }

  // Watch the HTML file for changes and auto-reload
  private void startFileWatcher(File htmlFile, WebEngine webEngine) {
    watchThread = new Thread(() -> {
      try {
        Path dir = htmlFile.getParentFile().toPath();
        WatchService watcher = FileSystems.getDefault().newWatchService();
        dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
        System.out.println("[SMCMAP] Watching: " + dir);

        while (!Thread.currentThread().isInterrupted()) {
          WatchKey key = watcher.take();
          boolean reload = false;
          for (WatchEvent<?> event : key.pollEvents()) {
            Path changed = (Path) event.context();
            if (changed.toString().equals(htmlFile.getName())) {
              reload = true;
            }
          }
          key.reset();
          if (reload) {
            // Small delay to let file write finish
            Thread.sleep(300);
            System.out.println("[SMCMAP] File changed - hot-reloading...");
            Platform.runLater(() -> loadHtml(webEngine));
          }
        }
      } catch (InterruptedException e) {
        // Normal shutdown
      } catch (Exception e) {
        System.err.println("[SMCMAP] File watcher error: " + e.getMessage());
      }
    }, "SMCMAP-HotReload");
    watchThread.setDaemon(true);
    watchThread.start();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
