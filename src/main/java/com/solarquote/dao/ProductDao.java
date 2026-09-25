package com.solarquote.dao;

import com.solarquote.db.Db;
import com.solarquote.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class ProductDao {

    private ProductDao() {}

    /** Products matching the search text (name/brand/category); blank search returns all. */
    public static List<Product> list(String search, boolean activeOnly) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE 1=1");
        if (activeOnly) sql.append(" AND active = 1");
        boolean hasSearch = search != null && !search.isBlank();
        if (hasSearch) sql.append(" AND (name LIKE ? OR brand LIKE ? OR category LIKE ?)");
        sql.append(" ORDER BY FIELD(category,'PANEL','INVERTER','BATTERY','STRUCTURE','CABLE','BOS','SERVICE','OTHER'), name");

        List<Product> list = new ArrayList<>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (hasSearch) {
                String like = "%" + search.trim() + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setString(3, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public static void save(Product p) throws SQLException {
        String sql = p.id == null
                ? "INSERT INTO products (name, category, brand, unit, price, gst_rate, wattage, active) VALUES (?,?,?,?,?,?,?,?)"
                : "UPDATE products SET name=?, category=?, brand=?, unit=?, price=?, gst_rate=?, wattage=?, active=? WHERE id=?";
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.name);
            ps.setString(2, p.category);
            ps.setString(3, p.brand);
            ps.setString(4, p.unit);
            ps.setBigDecimal(5, p.price);
            ps.setBigDecimal(6, p.gstRate);
            if (p.wattage == null) ps.setNull(7, Types.INTEGER);
            else ps.setInt(7, p.wattage);
            ps.setBoolean(8, p.active);
            if (p.id != null) ps.setInt(9, p.id);
            ps.executeUpdate();
            if (p.id == null) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) p.id = keys.getInt(1);
                }
            }
        }
    }

    public static void delete(int id) throws SQLException {
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.id = rs.getInt("id");
        p.name = rs.getString("name");
        p.category = rs.getString("category");
        p.brand = rs.getString("brand");
        p.unit = rs.getString("unit");
        p.price = rs.getBigDecimal("price");
        p.gstRate = rs.getBigDecimal("gst_rate");
        int w = rs.getInt("wattage");
        p.wattage = rs.wasNull() ? null : w;
        p.active = rs.getBoolean("active");
        return p;
    }
}
