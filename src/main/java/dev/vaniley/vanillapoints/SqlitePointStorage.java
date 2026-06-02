package dev.vaniley.vanillapoints;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqlitePointStorage extends SqlPointStorage {
    SqlitePointStorage(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String backendName() {
        return "sqlite";
    }

    @Override
    protected String driverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected String jdbcUrl() {
        String fileName = plugin.getConfig().getString("storage.sqlite.file", "storage.db");
        File file = new File(fileName == null || fileName.isBlank() ? "storage.db" : fileName);
        if (!file.isAbsolute()) {
            file = new File(plugin.getDataFolder(), file.getPath());
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new StorageException("Could not create SQLite storage directory: " + parent);
        }
        return "jdbc:sqlite:" + file.getAbsolutePath();
    }

    @Override
    protected int poolSize() {
        return 1;
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setConnectionTestQuery("SELECT 1");
    }

    @Override
    protected void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA journal_mode=WAL");
        }
    }
}
