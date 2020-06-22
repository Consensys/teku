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

package tech.pegasys.teku.storage.server.rocksdb.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;

/**
 * Provides an abstract "data access object" interface for working with ETH1 data from the
 * underlying database.
 */
public interface RocksDbEth1Dao extends AutoCloseable {

  @MustBeClosed
  Stream<DepositsFromBlockEvent> streamDepositsFromBlocks();

  Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock();

  Eth1Updater eth1Updater();

  interface Eth1Updater extends AutoCloseable {
    void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event);

    void addDepositsFromBlockEvent(final DepositsFromBlockEvent event);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
