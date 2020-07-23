/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.fs;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tech.pegasys.teku.storage.server.Database;

public class FsDatabaseFactory {

  public static Database create(final Path dbDir, final MetricsSystem metricsSystem) {
    final HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(
        "jdbc:h2:file:" + dbDir.resolve("index").toAbsolutePath() + ";mode=MySQL");

    final Flyway flyway = Flyway.configure().dataSource(dataSource).load();

    // Start the migration
    flyway.migrate();

    final PlatformTransactionManager transactionManager =
        new DataSourceTransactionManager(dataSource);
    final FsIndex index = new FsIndex(transactionManager, dataSource);

    final FsDatabase database =
        new FsDatabase(metricsSystem, new FsStorage(dbDir.resolve("artifacts"), index));
    return database;
  }
}
