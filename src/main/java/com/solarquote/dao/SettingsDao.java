package com.solarquote.dao;

import com.solarquote.db.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class SettingsDao {

    private SettingsDao() {}

    public static Map<String, String> loadAll() throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement("SELECT skey, svalue FROM settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        }
        return map;
    }

    public static void saveAll(Map<String, String> values) throws SQLException {
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO settings (skey, svalue) VALUES (?, ?) "
                     + "ON DUPLICATE KEY UPDATE svalue = VALUES(svalue)")) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
