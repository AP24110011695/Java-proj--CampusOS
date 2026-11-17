package com.campusos.config;

import com.campusos.exception.CampusOSException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Loads database configuration securely:
 *  1. Environment variables  CAMPUSOS_DB_URL / CAMPUSOS_DB_USER / CAMPUSOS_DB_PASSWORD
 *  2. External file  config/db.properties  (gitignored - never committed)
 *  3. Fallback file  db.properties  in the working directory
 *
 * No credentials are ever hardcoded in source code.
 */
public final class DatabaseConfig {

    private static DatabaseConfig instance;

    private final String url;
    private final String username;
    private final String password;
    private final int poolSize;
    private final String source;

    private DatabaseConfig(String url, String username, String password, int poolSize, String source) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        this.source = source;
    }

    public static synchronized DatabaseConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Used by tests to inject a known configuration. */
    static void setForTesting(DatabaseConfig config) {
        instance = config;
    }

    private static DatabaseConfig load() {
        String envUrl = System.getenv("CAMPUSOS_DB_URL");
        String envUser = System.getenv("CAMPUSOS_DB_USER");
        String envPass = System.getenv("CAMPUSOS_DB_PASSWORD");
        if (notBlank(envUrl) && notBlank(envUser)) {
            return new DatabaseConfig(envUrl, envUser, envPass == null ? "" : envPass,
                    10, "environment variables");
        }

        for (Path candidate : List.of(
                Path.of("config", "db.properties"),
                Path.of("db.properties"),
                Path.of(System.getProperty("user.home"), ".campusos", "db.properties"))) {
            if (Files.isRegularFile(candidate)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(candidate)) {
                    props.load(in);
                } catch (IOException e) {
                    throw new CampusOSException("Cannot read " + candidate.toAbsolutePath() + ": " + e.getMessage(), e);
                }
                String url = props.getProperty("db.url");
                String user = props.getProperty("db.username");
                String pass = props.getProperty("db.password", "");
                if (!notBlank(url) || !notBlank(user)) {
                    throw new CampusOSException("File " + candidate.toAbsolutePath()
                            + " must define db.url and db.username.");
                }
                int pool = parsePool(props.getProperty("db.pool.size", "10"));
                return new DatabaseConfig(url.trim(), user.trim(), pass, pool,
                        candidate.toAbsolutePath().toString());
            }
        }

        throw new CampusOSException("""
                Database configuration not found.

                CampusOS looks for credentials in this order:
                  1. Environment variables CAMPUSOS_DB_URL, CAMPUSOS_DB_USER, CAMPUSOS_DB_PASSWORD
                  2. File config/db.properties  (copy it from config/db.properties.example)
                  3. File db.properties in the current directory

                See the README "Database configuration" section for setup steps.""");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static int parsePool(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    /** Opens a fresh JDBC connection. Callers must close it (try-with-resources). */
    public java.sql.Connection newConnection() {
        try {
            return java.sql.DriverManager.getConnection(url, username, password);
        } catch (java.sql.SQLException e) {
            throw new com.campusos.exception.DataAccessException(
                    "Could not connect to MySQL using " + source
                            + ". Check that the MySQL server is running and the credentials are correct. ("
                            + e.getMessage() + ")", e);
        }
    }

    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getSource() { return source; }
    public int getPoolSize() { return poolSize; }
}
