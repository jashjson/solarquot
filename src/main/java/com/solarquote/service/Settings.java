package com.solarquote.service;

import com.solarquote.dao.SettingsDao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** In-memory cache of the settings table. Call {@link #reload()} after saving. */
public final class Settings {

    private static Map<String, String> values = new HashMap<>();

    private Settings() {}

    public static void reload() throws SQLException {
        values = SettingsDao.loadAll();
    }

    public static String get(String key) {
        String v = values.get(key);
        return v == null ? "" : v;
    }

    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static BigDecimal getDecimal(String key) {
        try {
            return new BigDecimal(get(key).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    public static boolean getBool(String key) {
        return Boolean.parseBoolean(get(key).trim());
    }
}
