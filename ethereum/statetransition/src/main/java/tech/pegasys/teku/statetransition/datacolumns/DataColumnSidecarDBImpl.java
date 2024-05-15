/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.ColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.api.SidecarUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

// FIXME: remove stinky joins
public class DataColumnSidecarDBImpl implements DataColumnSidecarDB {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final CombinedChainDataClient combinedChainDataClient;
  private final SidecarUpdateChannel sidecarUpdateChannel;
  private final AtomicInteger addCounter = new AtomicInteger();
  private int maxAddedSlot = 0;

  public DataColumnSidecarDBImpl(
      final CombinedChainDataClient combinedChainDataClient,
      final SidecarUpdateChannel sidecarUpdateChannel) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.sidecarUpdateChannel = sidecarUpdateChannel;
  }

  @Override
  public Optional<UInt64> getFirstIncompleteSlot() {
    return combinedChainDataClient.getFirstIncompleteSlot().join();
  }

  @Override
  public Optional<DataColumnSidecar> getSidecar(final DataColumnIdentifier identifier) {
    return combinedChainDataClient.getSidecar(identifier).join();
  }

  @Override
  public Stream<DataColumnIdentifier> streamColumnIdentifiers(final UInt64 slot) {
    return combinedChainDataClient.getDataColumnIdentifiers(slot).join().stream()
        .map(ColumnSlotAndIdentifier::identifier);
  }

  @Override
  public void setFirstIncompleteSlot(final UInt64 slot) {
    Optional<UInt64> oldValue = getFirstIncompleteSlot();
    sidecarUpdateChannel.onFirstIncompleteSlot(slot);
    if (oldValue.isEmpty() || !oldValue.get().equals(slot)) {
      long oldSlotColCount = oldValue.map(s -> streamColumnIdentifiers(s).count()).orElse(0L);
      long newSlotCount = streamColumnIdentifiers(slot).count();
      LOG.info(
          "[nyota] DataColumnSidecarDB: setFirstIncompleteSlot {} ({} cols) ~> {} ({} cols)",
          oldValue.map(UInt64::toString).orElse("NA"),
          oldSlotColCount,
          slot,
          newSlotCount);
    }
  }

  @Override
  public void addSidecar(final DataColumnSidecar sidecar) {
    sidecarUpdateChannel.onNewSidecar(sidecar);
    addCounter.incrementAndGet();
    int slot = sidecar.getSlot().intValue();
    synchronized (this) {
      if (slot > maxAddedSlot) {
        LOG.info(
            "[nyota] DataColumnSidecarDB.addSidecar: new slot: {}, prevSlot count: {}, total added: {}, finalizedSlot: {}",
            slot,
            streamColumnIdentifiers(UInt64.valueOf(maxAddedSlot)).count(),
            addCounter.get(),
            getFirstIncompleteSlot().orElse(UInt64.ONE).decrement());
        maxAddedSlot = slot;
      }
    }
  }

  @Override
  public void pruneAllSidecars(final UInt64 tillSlotExclusive) {
    sidecarUpdateChannel.onSidecarsAvailabilitySlot(tillSlotExclusive);
  }
}
