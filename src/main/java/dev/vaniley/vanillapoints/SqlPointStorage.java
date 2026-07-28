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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

abstract class SqlPointStorage extends AbstractPointStorage {
    private static final String POINT_TYPE_SPAWN = "SPAWN";
    private static final String POINT_TYPE_HOME = "HOME";
    private static final String POINT_TYPE_WARP = "WARP";

    protected final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private PointStorageSnapshot persistedSnapshot = PointStorageSnapshot.empty();

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
                persistedSnapshot = readSnapshot(connection);
                replace(persistedSnapshot);
            }
        } catch (ReflectiveOperationException | SQLException exception) {
            closeDataSource();
            throw new StorageException("Could not load " + backendName() + " storage", exception);
        }
    }

    @Override
    public final synchronized void save(PointStorageSnapshot snapshot) throws StorageException {
        Map<PointKey, StoredPoint> before = flatten(persistedSnapshot);
        Map<PointKey, StoredPoint> after = flatten(snapshot);
        List<PointKey> deletions = new ArrayList<>();
        List<Map.Entry<PointKey, StoredPoint>> writes = new ArrayList<>();

        before.keySet().stream().filter(key -> !after.containsKey(key)).forEach(deletions::add);
        after.entrySet().stream()
                .filter(entry -> !entry.getValue().equals(before.get(entry.getKey())))
                .forEach(entry -> {
                    deletions.add(entry.getKey());
                    writes.add(entry);
                });

        if (deletions.isEmpty() && writes.isEmpty()) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            configureConnection(connection);
            connection.setAutoCommit(false);
            try {
                deletePoints(connection, deletions);
                insertPoints(connection, writes);
                connection.commit();
                persistedSnapshot = snapshot;
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
        closeDataSource();
    }

    private void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
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
        config.setConnectionTimeout(Math.max(250L, plugin == null ? 10_000L
                : plugin.getConfig().getLong("storage.connection-timeout-ms", 10_000L)));
        config.setValidationTimeout(Math.max(250L, plugin == null ? 5_000L
                : plugin.getConfig().getLong("storage.validation-timeout-ms", 5_000L)));
        config.setInitializationFailTimeout(1L);
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
                    + "category VARCHAR(64),"
                    + "is_public INTEGER NOT NULL DEFAULT 1,"
                    + "created_by VARCHAR(64),"
                    + "created_at BIGINT NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (point_type, owner_uuid, point_name)"
                    + ")");
        }

        addColumnIfMissing(connection, "description", "TEXT");
        addColumnIfMissing(connection, "icon", "VARCHAR(64)");
        addColumnIfMissing(connection, "category", "VARCHAR(64)");
        addColumnIfMissing(connection, "is_public", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(connection, "created_by", "VARCHAR(64)");
        addColumnIfMissing(connection, "created_at", "BIGINT NOT NULL DEFAULT 0");

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM vanillapoints_schema")) {
            if (resultSet.next() && resultSet.getInt(1) == 0) {
                try (Statement insertStatement = connection.createStatement()) {
                    insertStatement.executeUpdate("INSERT INTO vanillapoints_schema (version) VALUES (2)");
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
        Map<String, StoredPoint> spawns = new HashMap<>();
        Map<UUID, Map<String, StoredPoint>> homes = new HashMap<>();
        Map<String, StoredPoint> warps = new HashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT point_type, owner_uuid, point_name, world, x, y, z, yaw, pitch, description, icon, category, is_public, created_by, created_at FROM vanillapoints_points")) {
            while (resultSet.next()) {
                String type = resultSet.getString("point_type");
                StoredPoint point = readPoint(resultSet);
                if (POINT_TYPE_SPAWN.equals(type)) {
                    String worldKey = resultSet.getString("point_name");
                    spawns.put(worldKey == null ? PointStorageSnapshot.GLOBAL_SPAWN_KEY : worldKey, point);
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

        return new PointStorageSnapshot(spawns, homes, warps);
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
                resultSet.getString("category"),
                resultSet.getInt("is_public") != 0,
                resultSet.getString("created_by"),
                resultSet.getLong("created_at")
        );
    }

    private Map<PointKey, StoredPoint> flatten(PointStorageSnapshot snapshot) {
        Map<PointKey, StoredPoint> points = new HashMap<>();
        snapshot.spawns().forEach((name, point) -> points.put(new PointKey(POINT_TYPE_SPAWN, "", name), point));
        snapshot.homes().forEach((owner, homes) -> homes.forEach((name, point) ->
                points.put(new PointKey(POINT_TYPE_HOME, owner.toString(), name), point)));
        snapshot.warps().forEach((name, point) -> points.put(new PointKey(POINT_TYPE_WARP, "", name), point));
        return points;
    }

    private void deletePoints(Connection connection, List<PointKey> keys) throws SQLException {
        if (keys.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM vanillapoints_points "
                + "WHERE point_type = ? AND owner_uuid = ? AND point_name = ?")) {
            for (PointKey key : keys) {
                statement.setString(1, key.type());
                statement.setString(2, key.ownerUuid());
                statement.setString(3, key.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertPoints(Connection connection, List<Map.Entry<PointKey, StoredPoint>> points) throws SQLException {
        if (points.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO vanillapoints_points "
                + "(point_type, owner_uuid, point_name, world, x, y, z, yaw, pitch, description, icon, category, is_public, created_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Map.Entry<PointKey, StoredPoint> entry : points) {
                PointKey key = entry.getKey();
                addPoint(statement, key.type(), key.ownerUuid(), key.name(), entry.getValue());
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
        statement.setString(12, point.category().isBlank() ? null : point.category());
        statement.setInt(13, point.publicVisible() ? 1 : 0);
        statement.setString(14, point.createdBy().isBlank() ? null : point.createdBy());
        statement.setLong(15, point.createdAt());
        statement.addBatch();
    }

    private record PointKey(String type, String ownerUuid, String name) {
    }
}
