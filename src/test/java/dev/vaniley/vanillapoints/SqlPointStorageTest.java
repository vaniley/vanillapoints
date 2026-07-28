package dev.vaniley.vanillapoints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlPointStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void sqlitePersistsPointsAcrossReloads() {
        String url = "jdbc:sqlite:" + tempDir.resolve("points.db");
        StoredPoint point = point(10.5D);

        try (TestSqlStorage storage = new TestSqlStorage(url)) {
            storage.load();
            storage.setWarp("shop", point);
            storage.save(storage.snapshot());
        }

        try (TestSqlStorage storage = new TestSqlStorage(url)) {
            storage.load();
            assertEquals(point, storage.warp("shop").orElseThrow());
        }
    }

    @Test
    void saveOnlyRewritesChangedRows() throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("incremental.db");
        try (TestSqlStorage storage = new TestSqlStorage(url)) {
            storage.load();
            storage.setWarp("shop", point(10.0D));
            storage.setWarp("mine", point(20.0D));
            storage.save(storage.snapshot());

            try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE delete_audit (count INTEGER NOT NULL)");
                statement.executeUpdate("INSERT INTO delete_audit VALUES (0)");
                statement.executeUpdate("CREATE TRIGGER count_point_deletes AFTER DELETE ON vanillapoints_points "
                        + "BEGIN UPDATE delete_audit SET count = count + 1; END");
            }

            storage.setWarp("shop", point(11.0D));
            storage.save(storage.snapshot());
        }

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT count FROM delete_audit")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
    }

    private StoredPoint point(double x) {
        return StoredPoint.of("world", x, 64.0D, -4.0D, 0.0F, 0.0F,
                "", "", "", true, "test", 1L);
    }

    private static final class TestSqlStorage extends SqlPointStorage {
        private final String url;

        private TestSqlStorage(String url) {
            super(null);
            this.url = url;
        }

        @Override
        protected String backendName() {
            return "test-sqlite";
        }

        @Override
        protected String driverClassName() {
            return "org.sqlite.JDBC";
        }

        @Override
        protected String jdbcUrl() {
            return url;
        }

        @Override
        protected int poolSize() {
            return 1;
        }
    }
}
