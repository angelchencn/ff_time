package oracle.apps.hcm.formulas.core.jersey.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection factory — reads config from environment variables.
 *
 * Required env vars:
 *   FF_DB_URL      = jdbc:oracle:thin:@//host:1521/service
 *   FF_DB_USER     = username
 *   FF_DB_PASSWORD = password
 */
public class DbConfig {

    private static final String URL = System.getenv("FF_DB_URL");
    private static final String USER = System.getenv("FF_DB_USER");
    private static final String PASSWORD = System.getenv("FF_DB_PASSWORD");

    public static Connection getConnection() throws SQLException {
        if (URL == null || URL.isBlank()) {
            throw new SQLException("FF_DB_URL environment variable is not set");
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
