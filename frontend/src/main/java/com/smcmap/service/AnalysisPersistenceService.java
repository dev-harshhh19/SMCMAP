package com.smcmap.service;

import com.smcmap.model.SystemSnapshot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AnalysisPersistenceService {

  private static final String DB_URL = "jdbc:sqlite:smcmap.db";

  public AnalysisPersistenceService() {
    initializeDatabase();
  }

  private void initializeDatabase() {
    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      if (conn != null) {
        try (Statement stmt = conn.createStatement()) {
          stmt.execute("CREATE TABLE IF NOT EXISTS system_snapshot (" +
              "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
              "total_ram BIGINT, " +
              "used_ram BIGINT, " +
              "free_ram BIGINT, " +
              "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
              ");");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void saveSystemSnapshot(SystemSnapshot snapshot) {
    String sql = "INSERT INTO system_snapshot(total_ram, used_ram, free_ram) VALUES(?,?,?)";

    try (Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, snapshot.getTotalRam());
      pstmt.setLong(2, snapshot.getUsedRam());
      pstmt.setLong(3, snapshot.getFreeRam());
      pstmt.executeUpdate();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public List<String[]> getHistoricalSnapshotsWithTime(int limit) {
    List<String[]> list = new ArrayList<>();
    String sql = "SELECT total_ram, used_ram, free_ram, timestamp FROM system_snapshot ORDER BY id DESC LIMIT ?";
    try (Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, limit);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        String tr = String.valueOf(rs.getLong("total_ram") / (1024 * 1024));
        String ur = String.valueOf(rs.getLong("used_ram") / (1024 * 1024));
        String fr = String.valueOf(rs.getLong("free_ram") / (1024 * 1024));
        String ts = rs.getString("timestamp");
        list.add(0, new String[] { tr, ur, fr, ts }); // Reverse so oldest is first
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }

  public List<String[]> getSnapshotsLastMinutes(int minutes) {
    List<String[]> list = new ArrayList<>();
    String sql = "SELECT total_ram, used_ram, free_ram, timestamp FROM system_snapshot WHERE timestamp >= datetime('now', '-"
        + minutes + " minutes') ORDER BY id ASC";
    try (Connection conn = DriverManager.getConnection(DB_URL);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      while (rs.next()) {
        String tr = String.valueOf(rs.getLong("total_ram") / (1024 * 1024));
        String ur = String.valueOf(rs.getLong("used_ram") / (1024 * 1024));
        String fr = String.valueOf(rs.getLong("free_ram") / (1024 * 1024));
        String ts = rs.getString("timestamp");
        list.add(new String[] { tr, ur, fr, ts });
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }

  public List<SystemSnapshot> getHistoricalSnapshots(int limit) {
    List<SystemSnapshot> list = new ArrayList<>();
    String sql = "SELECT total_ram, used_ram, free_ram FROM system_snapshot ORDER BY id DESC LIMIT ?";
    try (Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, limit);
      ResultSet rs = pstmt.executeQuery();
      while (rs.next()) {
        SystemSnapshot s = new SystemSnapshot();
        s.setTotalRam(rs.getLong("total_ram"));
        s.setUsedRam(rs.getLong("used_ram"));
        s.setFreeRam(rs.getLong("free_ram"));
        list.add(s);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }

  public int getSnapshotCount() {
    String sql = "SELECT COUNT(*) FROM system_snapshot";
    try (Connection conn = DriverManager.getConnection(DB_URL);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      if (rs.next())
        return rs.getInt(1);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return 0;
  }

  public long getDbSizeKB() {
    try {
      java.io.File f = new java.io.File("smcmap.db");
      if (f.exists())
        return f.length() / 1024;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return 0;
  }
}
