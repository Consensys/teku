package tech.pegasys.teku.storage.server.fs;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tech.pegasys.teku.storage.server.Database;

public class FsDatabaseFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static Database create(final Path dbDir, final MetricsSystem metricsSystem) {
    final HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl("jdbc:h2:file:" + dbDir.resolve("index").toAbsolutePath());

    final Flyway flyway = Flyway.configure().dataSource(dataSource).load();

    flyway.clean();
    // Start the migration
    flyway.migrate();

    final PlatformTransactionManager transactionManager =
        new DataSourceTransactionManager(dataSource);
    final FsIndex index = new FsIndex(transactionManager, new JdbcTemplate(dataSource));

    final FsDatabase database =
        new FsDatabase(metricsSystem, new FsStorage(dbDir.resolve("artifacts"), index));
    LOG.info("Created FS database");
    return database;
  }
}
