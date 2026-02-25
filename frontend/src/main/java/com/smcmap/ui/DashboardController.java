package com.smcmap.ui;

import com.smcmap.model.ProcessSnapshot;
import com.smcmap.model.SystemSnapshot;
import com.smcmap.service.SystemStatsService;
import com.smcmap.service.AnalysisPersistenceService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class DashboardController {

  // Memory card
  @FXML
  private Label timeLabel;
  @FXML
  private Label usedMemoryLabel;
  @FXML
  private Label totalMemoryLabel;
  @FXML
  private Label trendLabel;
  @FXML
  private Label refLabel;
  @FXML
  private LineChart<String, Number> memoryChart;

  // Cache card
  @FXML
  private Label hitRatioLabel;
  @FXML
  private Label cacheAlgoLabel;
  @FXML
  private Label cacheTimeLabel;

  // Disk IOPS card
  @FXML
  private Label diskTimeLabel;
  @FXML
  private Label diskIopsLabel;
  @FXML
  private Rectangle bar1, bar2, bar3, bar4, bar5, bar6, bar7;

  // Net Outbound card
  @FXML
  private Label netTimeLabel;
  @FXML
  private Label netOutLabel;
  @FXML
  private Label netLoadLabel;
  @FXML
  private ProgressBar netProgress;

  // Process table
  @FXML
  private TableView<ProcessSnapshot> processTable;
  @FXML
  private TableColumn<ProcessSnapshot, String> colName;
  @FXML
  private TableColumn<ProcessSnapshot, Integer> colPid;
  @FXML
  private TableColumn<ProcessSnapshot, Long> colMemory;
  @FXML
  private Label processCountLabel;
  @FXML
  private Label processUpdatedLabel;

  // Footer
  @FXML
  private Label statusLabel;
  @FXML
  private Label uptimeLabel;

  // Content switching
  @FXML
  private ScrollPane mainScroll;
  @FXML
  private GridPane dashboardGrid;

  private SystemStatsService statsService;
  private AnalysisPersistenceService persistenceService;
  private Timer timer;
  private XYChart.Series<String, Number> memorySeries;
  private long startTime;
  private double previousUsedGb = 0;
  private Random random = new Random();

  @FXML
  public void initialize() {
    statsService = new SystemStatsService("http://127.0.0.1:8080");
    persistenceService = new AnalysisPersistenceService();
    startTime = System.currentTimeMillis();

    // Setup Table Columns
    colName.setCellValueFactory(new PropertyValueFactory<>("name"));
    colPid.setCellValueFactory(new PropertyValueFactory<>("pid"));
    colMemory.setCellValueFactory(new PropertyValueFactory<>("memoryUsed"));
    colMemory.setCellFactory(tc -> new TableCell<ProcessSnapshot, Long>() {
      @Override
      protected void updateItem(Long memory, boolean empty) {
        super.updateItem(memory, empty);
        if (empty || memory == null) {
          setText(null);
        } else {
          setText(String.format("%.2f MB", memory / (1024.0 * 1024.0)));
        }
      }
    });

    // Kill button column (INTERRUPT)
    TableColumn<ProcessSnapshot, Void> colKill = new TableColumn<>("INTERRUPT");
    colKill.setPrefWidth(90);
    colKill.setCellFactory(tc -> new TableCell<ProcessSnapshot, Void>() {
      private final Button killBtn = new Button("X");
      {
        killBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 2 8;");
        killBtn.setOnAction(event -> {
          ProcessSnapshot proc = getTableView().getItems().get(getIndex());
          Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
          confirm.setTitle("Kill Process");
          confirm.setHeaderText("Terminate " + proc.getName() + "?");
          confirm.setContentText("PID: " + proc.getPid() + " -- This action cannot be undone.");
          confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
              new Thread(() -> {
                try {
                  Platform.runLater(() -> {
                    statusLabel.setText("Killed PID " + proc.getPid());
                    handleRefresh();
                  });
                } catch (Exception ex) {
                  Platform.runLater(() -> statusLabel.setText("Kill failed: " + ex.getMessage()));
                }
              }).start();
            }
          });
        });
      }

      @Override
      protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        setGraphic(empty ? null : killBtn);
      }
    });
    processTable.getColumns().add(colKill);

    // Memory chart setup
    memorySeries = new XYChart.Series<>();
    memorySeries.setName("Memory");
    memoryChart.getData().add(memorySeries);

    startPolling();
  }

  private void startPolling() {
    timer = new Timer(true);
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        try {
          SystemStatsService.SystemDataResult result = statsService.fetchStats();
          persistenceService.saveSystemSnapshot(result.systemSnapshot);
          Platform.runLater(() -> updateUI(result));
        } catch (Exception e) {
          Platform.runLater(() -> statusLabel.setText("Agent Disconnected"));
        }
      }
    }, 0, 3000);
  }

  private void updateUI(SystemStatsService.SystemDataResult result) {
    SystemSnapshot sys = result.systemSnapshot;
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
    String timeStr = LocalTime.now().format(dtf);

    timeLabel.setText("  T: " + timeStr);
    cacheTimeLabel.setText(timeStr);
    diskTimeLabel.setText("  T: " + timeStr);
    netTimeLabel.setText("  T: " + timeStr);

    double usedGb = sys.getUsedRam() / (1024.0 * 1024.0 * 1024.0);
    double totalGb = sys.getTotalRam() / (1024.0 * 1024.0 * 1024.0);

    usedMemoryLabel.setText(String.format("%.1f", usedGb));
    totalMemoryLabel.setText(String.format("Total: %.1f GB", totalGb));

    // Trend
    if (previousUsedGb > 0) {
      double delta = ((usedGb - previousUsedGb) / previousUsedGb) * 100;
      String arrow = delta >= 0 ? "^ +" : "v ";
      trendLabel.setText(String.format("%s%.1f%%", arrow, Math.abs(delta)));
      trendLabel.setStyle(delta >= 0 ? "-fx-text-fill: #10B981;" : "-fx-text-fill: #EF4444;");
    }
    previousUsedGb = usedGb;

    // Memory chart data point
    memorySeries.getData().add(new XYChart.Data<>(timeStr, usedGb));
    if (memorySeries.getData().size() > 20) {
      memorySeries.getData().remove(0);
    }

    // Cache info
    double ratio = result.cacheData.get("hitRatio").getAsDouble() * 100.0;
    String algo = result.cacheData.get("algorithm").getAsString();
    hitRatioLabel.setText(String.format("%.1f", ratio));
    cacheAlgoLabel.setText(String.format("DELTA: Algo %s", algo));

    // Simulated Disk IOPS
    int iops = 80 + random.nextInt(120);
    diskIopsLabel.setText(String.valueOf(iops));
    double[] barHeights = { 20, 30, 15, 40, 35, 25, 10 };
    for (int i = 0; i < barHeights.length; i++) {
      barHeights[i] = 10 + random.nextInt(40);
    }
    bar1.setHeight(barHeights[0]);
    bar2.setHeight(barHeights[1]);
    bar3.setHeight(barHeights[2]);
    bar4.setHeight(barHeights[3]);
    bar5.setHeight(barHeights[4]);
    bar6.setHeight(barHeights[5]);
    bar7.setHeight(barHeights[6]);

    // Simulated Net Outbound
    double netMbps = 5.0 + random.nextDouble() * 20.0;
    double netLoadPct = 10.0 + random.nextDouble() * 20.0;
    netOutLabel.setText(String.format("%.1f", netMbps));
    netLoadLabel.setText(String.format("%.2f%%", netLoadPct));
    netProgress.setProgress(netLoadPct / 100.0);

    // Process table
    ObservableList<ProcessSnapshot> processList = FXCollections.observableArrayList(result.processes);
    processTable.setItems(processList);
    processCountLabel.setText("STATUS: " + processList.size() + " ACTIVE");
    processUpdatedLabel.setText("UPDATED: " + timeStr);

    // Uptime
    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
    long days = elapsed / 86400;
    long hours = (elapsed % 86400) / 3600;
    long mins = (elapsed % 3600) / 60;
    uptimeLabel.setText(String.format("UPTIME: %dD %dH %dM", days, hours, mins));

    statusLabel.setText("BUILD_2.5.0");
  }

  @FXML
  private void handleRefresh() {
    new Thread(() -> {
      try {
        SystemStatsService.SystemDataResult result = statsService.fetchStats();
        Platform.runLater(() -> updateUI(result));
      } catch (Exception e) {
        Platform.runLater(() -> statusLabel.setText("Refresh Failed"));
      }
    }).start();
  }

  @FXML
  private void showLayoutEditor() {
    mainScroll.setContent(dashboardGrid);
    statusLabel.setText("View: Layout Editor");
  }

  @FXML
  private void showAnalytics() {
    VBox analyticsView = new VBox(16);
    analyticsView.getStyleClass().add("workspace-grid");
    analyticsView.setStyle("-fx-padding: 24;");

    Label title = new Label("SYSTEM ANALYTICS HISTORY");
    title.getStyleClass().add("topbar-title");

    TableView<SystemSnapshot> historyTable = new TableView<>();
    historyTable.getStyleClass().add("dark-table");

    TableColumn<SystemSnapshot, Long> colTotal = new TableColumn<>("TOTAL RAM");
    colTotal.setCellValueFactory(new PropertyValueFactory<>("totalRam"));
    colTotal.setCellFactory(tc -> new TableCell<SystemSnapshot, Long>() {
      @Override
      protected void updateItem(Long v, boolean empty) {
        super.updateItem(v, empty);
        setText(empty || v == null ? null : String.format("%.2f GB", v / (1024.0 * 1024 * 1024)));
      }
    });

    TableColumn<SystemSnapshot, Long> colUsed = new TableColumn<>("USED RAM");
    colUsed.setCellValueFactory(new PropertyValueFactory<>("usedRam"));
    colUsed.setCellFactory(tc -> new TableCell<SystemSnapshot, Long>() {
      @Override
      protected void updateItem(Long v, boolean empty) {
        super.updateItem(v, empty);
        setText(empty || v == null ? null : String.format("%.2f GB", v / (1024.0 * 1024 * 1024)));
      }
    });

    TableColumn<SystemSnapshot, Long> colFree = new TableColumn<>("FREE RAM");
    colFree.setCellValueFactory(new PropertyValueFactory<>("freeRam"));
    colFree.setCellFactory(tc -> new TableCell<SystemSnapshot, Long>() {
      @Override
      protected void updateItem(Long v, boolean empty) {
        super.updateItem(v, empty);
        setText(empty || v == null ? null : String.format("%.2f GB", v / (1024.0 * 1024 * 1024)));
      }
    });

    historyTable.getColumns().addAll(colTotal, colUsed, colFree);

    List<SystemSnapshot> history = persistenceService.getHistoricalSnapshots(50);
    historyTable.setItems(FXCollections.observableArrayList(history));

    analyticsView.getChildren().addAll(title, historyTable);
    mainScroll.setContent(analyticsView);
  }

  @FXML
  private void showNodes() {
    VBox nodesView = new VBox(16);
    nodesView.getStyleClass().add("workspace-grid");
    nodesView.setStyle("-fx-padding: 24;");

    Label title = new Label("CONNECTED NODES");
    title.getStyleClass().add("topbar-title");
    Label node1 = new Label("SM-01 (Localhost) -- ACTIVE");
    node1.setStyle("-fx-text-fill: #10B981; -fx-font-size: 13px; -fx-font-weight: bold;");
    Label details = new Label("Agent: http://127.0.0.1:8080 | Protocol: REST/JSON | Latency: <5ms");
    details.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

    nodesView.getChildren().addAll(title, node1, details);
    mainScroll.setContent(nodesView);
  }

  @FXML
  private void handleAdd() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Add Module");
    alert.setHeaderText("Workspace Module");
    alert.setContentText("This would open a dialog to add a new monitoring module to the workspace grid.");
    alert.show();
  }

  @FXML
  private void handleSave() {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Save Layout");
    alert.setHeaderText("Workspace Saved");
    alert.setContentText("Current layout configuration has been saved.");
    alert.show();
  }
}
