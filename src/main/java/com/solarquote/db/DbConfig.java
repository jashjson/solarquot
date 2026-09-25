package com.solarquote.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * MySQL connection details, stored in ~/.solarquote/db.properties.
 * Everything else (company details, terms, ...) lives in the database itself.
 */
public class DbConfig {

    public static final Path DIR  = Path.of(System.getProperty("user.home"), ".solarquote");
    public static final Path FILE = DIR.resolve("db.properties");

    public String host = "localhost";
    public String port = "3306";
    public String database = "solarquote";
    public String user = "root";
    public String password = "";

    public static DbConfig load() {
        DbConfig c = new DbConfig();
        if (Files.exists(FILE)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
                c.host = p.getProperty("host", c.host);
                c.port = p.getProperty("port", c.port);
                c.database = p.getProperty("database", c.database);
                c.user = p.getProperty("user", c.user);
                c.password = p.getProperty("password", c.password);
            } catch (IOException ignored) {
                // fall back to defaults; the connection dialog lets the user fix it
            }
        }
        return c;
    }

    public void save() throws IOException {
        Files.createDirectories(DIR);
        Properties p = new Properties();
        p.setProperty("host", host);
        p.setProperty("port", port);
        p.setProperty("database", database);
        p.setProperty("user", user);
        p.setProperty("password", password);
        try (OutputStream out = Files.newOutputStream(FILE)) {
            p.store(out, "SolarQuote database connection");
        }
    }

    /** JDBC URL; the database is created automatically on first connect. */
    public String url() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8&serverTimezone=Asia/Kolkata";
    }
}
