/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.leveldb;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.Collection;
import org.fusesource.leveldbjni.JniDBFactory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration;
import tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn;

public class LevelDbInstanceFactory {
  public static KvStoreAccessor create(
      final MetricsSystem metricsSystem,
      final MetricCategory metricCategory,
      final KvStoreConfiguration configuration,
      final Collection<KvStoreColumn<?, ?>> columns)
      throws DatabaseStorageException {
    checkArgument(
        columns.stream().map(KvStoreColumn::getId).distinct().count() == columns.size(),
        "Column IDs are not distinct");
    final Options options =
        new Options()
            .createIfMissing(true)
            .maxOpenFiles(configuration.getLeveldbMaxOpenFiles())
            .blockSize(configuration.getLeveldbBlockSize())
            .writeBufferSize(configuration.getLeveldbWriteBufferSize());

    try {
      final DB db = JniDBFactory.factory.open(configuration.getDatabaseDir().toFile(), options);
      return new LevelDbInstance(db, metricsSystem, metricCategory);
    } catch (final IOException e) {
      throw DatabaseStorageException.unrecoverable("Failed to open database", e);
    }
  }
}
