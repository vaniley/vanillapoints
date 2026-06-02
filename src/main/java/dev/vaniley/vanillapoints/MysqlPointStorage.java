package dev.vaniley.vanillapoints;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.plugin.java.JavaPlugin;

final class MysqlPointStorage extends SqlPointStorage {
    MysqlPointStorage(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String backendName() {
        return "mysql";
    }

    @Override
    protected String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    protected String jdbcUrl() {
        String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfig().getString("storage.mysql.database", "vanillapoints");
        boolean useSsl = plugin.getConfig().getBoolean("storage.mysql.use-ssl", true);
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSsl
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC";
    }

    @Override
    protected int poolSize() {
        return plugin.getConfig().getInt("storage.mysql.pool-size", 8);
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setUsername(plugin.getConfig().getString("storage.mysql.username", "root"));
        config.setPassword(plugin.getConfig().getString("storage.mysql.password", ""));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
    }
}
