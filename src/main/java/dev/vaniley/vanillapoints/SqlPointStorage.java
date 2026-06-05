package dev.vaniley.vanillapoints;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

abstract class SqlPointStorage extends AbstractPointStorage {
    private static final String POINT_TYPE_SPAWN = "SPAWN";
    private static final String POINT_TYPE_HOME = "HOME";
    private static final String POINT_TYPE_WARP = "WARP";

    protected final JavaPlugin plugin;
    private HikariDataSource dataSource;

    SqlPointStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public final void load() throws StorageException {
        try {
            loadDriver();
            dataSource = createDataSource();
            try (Connection connection = dataSource.getConnection()) {
                configureConnection(connection);
                migrateSchema(connection);
                replace(readSnapshot(connection));
            }
        } catch (ReflectiveOperationException | SQLException exception) {
            throw new StorageException("Could not load " + backendName() + " storage", exception);
        }
    }

    @Override
    public final void save(PointStorageSnapshot snapshot) throws StorageException {
        try (Connection connection = dataSource.getConnection()) {
            configureConnection(connection);
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM vanillapoints_points");
                }
                writeSnapshot(connection, snapshot);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new StorageException("Could not save " + backendName() + " storage", exception);
        }
    }

    @Override
    public final void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    protected abstract String backendName();

    protected abstract String driverClassName();

    protected abstract String jdbcUrl();

    protected abstract int poolSize();

    protected void configure(HikariConfig config) {
    }

    protected void configureConnection(Connection connection) throws SQLException {
    }

    private void loadDriver() throws ReflectiveOperationException {
        Class.forName(driverClassName());
    }

    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setMaximumPoolSize(Math.max(1, poolSize()));
        config.setPoolName("VanillaPoints-" + backendName());
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);
        configure(config);
        return new HikariDataSource(config);
    }

    private void migrateSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vanillapoints_schema (version INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS vanillapoints_points ("
                    + "point_type VARCHAR(16) NOT NULL,"
                    + "owner_uuid VARCHAR(36) NOT NULL DEFAULT '',"
                    + "point_name VARCHAR(64) NOT NULL DEFAULT '',"
                    + "world VARCHAR(255) NOT NULL,"
                    + "x DOUBLE NOT NULL,"
                    + "y DOUBLE NOT NULL,"
                    + "z DOUBLE NOT NULL,"
                    + "yaw FLOAT NOT NULL DEFAULT 0,"
                    + "pitch FLOAT NOT NULL DEFAULT 0,"
                    + "description TEXT,"
                    + "icon VARCHAR(64),"
                    + "created_by VARCHAR(64),"
                    + "created_at BIGINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (point_type, owner_uuid, point_name)"
                    + ")");
        }

        addColumnIfMissing(connection, "description", "TEXT");
        addColumnIfMissing(connection, "icon", "VARCHAR(64)");
        addColumnIfMissing(connection, "created_by", "VARCHAR(64)");
        addColumnIfMissing(connection, "created_at", "BIGINT NOT NULL DEFAULT 0");

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM vanillapoints_schema")) {
            if (resultSet.next() && resultSet.getInt(1) == 0) {
                try (Statement insertStatement = connection.createStatement()) {
                    insertStatement.executeUpdate("INSERT INTO vanillapoints_schema (version) VALUES (1)");
                }
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String columnName, String definition) throws SQLException {
        if (hasColumn(connection, columnName)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE vanillapoints_points ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean hasColumn(Connection connection, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, "vanillapoints_points", columnName)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getColumns(null, null, "VANILLAPOINTS_POINTS", columnName.toUpperCase())) {
            return resultSet.next();
        }
    }

    private PointStorageSnapshot readSnapshot(Connection connection) throws SQLException {
        StoredPoint spawn = null;
        Map<UUID, Map<String, StoredPoint>> homes = new HashMap<>();
        Map<String, StoredPoint> warps = new HashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT point_type, owner_uuid, point_name, world, x, y, z, yaw, pitch, description, icon, created_by, created_at FROM vanillapoints_points")) {
            while (resultSet.next()) {
                String type = resultSet.getString("point_type");
                StoredPoint point = readPoint(resultSet);
                if (POINT_TYPE_SPAWN.equals(type)) {
                    spawn = point;
                } else if (POINT_TYPE_HOME.equals(type)) {
                    try {
                        String rawName = resultSet.getString("point_name");
                        String homeName = rawName == null || rawName.isBlank() ? PointStorage.DEFAULT_HOME_NAME : rawName;
                        if (PointStorage.isValidHomeName(homeName)) {
                            homes.computeIfAbsent(UUID.fromString(resultSet.getString("owner_uuid")), ignored -> new HashMap<>())
                                    .put(PointStorage.normalizeHomeName(homeName), point);
                        } else {
                            plugin.getLogger().warning("Skipping SQL home with invalid name: " + homeName);
                        }
                    } catch (IllegalArgumentException exception) {
                        plugin.getLogger().warning("Skipping SQL home with invalid UUID: " + resultSet.getString("owner_uuid"));
                    }
                } else if (POINT_TYPE_WARP.equals(type)) {
                    String name = resultSet.getString("point_name");
                    if (PointStorage.isValidWarpName(name)) {
                        warps.put(PointStorage.normalizeWarpName(name), point);
                    } else {
                        plugin.getLogger().warning("Skipping SQL warp with invalid name: " + name);
                    }
                }
            }
        }

        return new PointStorageSnapshot(spawn, homes, warps);
    }

    private StoredPoint readPoint(ResultSet resultSet) throws SQLException {
        return StoredPoint.of(
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch"),
                resultSet.getString("description"),
                resultSet.getString("icon"),
                resultSet.getString("created_by"),
                resultSet.getLong("created_at")
        );
    }

    private void writeSnapshot(Connection connection, PointStorageSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO vanillapoints_points "
                + "(point_type, owner_uuid, point_name, world, x, y, z, yaw, pitch, description, icon, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            if (snapshot.spawn() != null) {
                addPoint(statement, POINT_TYPE_SPAWN, "", "", snapshot.spawn());
            }
            for (Map.Entry<UUID, Map<String, StoredPoint>> entry : snapshot.homes().entrySet()) {
                for (Map.Entry<String, StoredPoint> homeEntry : entry.getValue().entrySet()) {
                    addPoint(statement, POINT_TYPE_HOME, entry.getKey().toString(), homeEntry.getKey(), homeEntry.getValue());
                }
            }
            for (Map.Entry<String, StoredPoint> entry : snapshot.warps().entrySet()) {
                addPoint(statement, POINT_TYPE_WARP, "", entry.getKey(), entry.getValue());
            }
            statement.executeBatch();
        }
    }

    private void addPoint(PreparedStatement statement, String type, String ownerUuid, String name, StoredPoint point) throws SQLException {
        statement.setString(1, type);
        statement.setString(2, ownerUuid);
        statement.setString(3, name);
        statement.setString(4, point.worldName());
        statement.setDouble(5, point.x());
        statement.setDouble(6, point.y());
        statement.setDouble(7, point.z());
        statement.setFloat(8, point.yaw());
        statement.setFloat(9, point.pitch());
        statement.setString(10, point.description().isBlank() ? null : point.description());
        statement.setString(11, point.icon().isBlank() ? null : point.icon());
        statement.setString(12, point.createdBy().isBlank() ? null : point.createdBy());
        statement.setLong(13, point.createdAt());
        statement.addBatch();
    }
}
