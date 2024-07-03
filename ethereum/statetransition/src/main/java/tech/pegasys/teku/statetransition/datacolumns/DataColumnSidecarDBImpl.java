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

import it.unimi.dsi.fastutil.Pair;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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
  public SafeFuture<Optional<UInt64>> getFirstIncompleteSlot() {
    return combinedChainDataClient.getFirstIncompleteSlot();
  }

  @Override
  public SafeFuture<Optional<DataColumnSidecar>> getSidecar(final DataColumnIdentifier identifier) {
    return combinedChainDataClient.getSidecar(identifier);
  }

  @Override
  public SafeFuture<Stream<DataColumnIdentifier>> streamColumnIdentifiers(final UInt64 slot) {
    return combinedChainDataClient
        .getDataColumnIdentifiers(slot)
        .thenApply(identifiers -> identifiers.stream().map(ColumnSlotAndIdentifier::identifier));
  }

  @Override
  public SafeFuture<Void> setFirstIncompleteSlot(final UInt64 slot) {
    return getFirstIncompleteSlot()
        .thenAccept(
            maybeFirstIncompleteSlot -> {
              sidecarUpdateChannel.onFirstIncompleteSlot(slot);
              if (maybeFirstIncompleteSlot.isEmpty()
                  || !maybeFirstIncompleteSlot.get().equals(slot)) {
                streamColumnIdentifiers(slot)
                    .thenCompose(
                        newSlotColumnIdentifiers -> {
                          final long newSlotCount = newSlotColumnIdentifiers.count();
                          if (maybeFirstIncompleteSlot.isEmpty()) {
                            return SafeFuture.completedFuture(Pair.of(0L, newSlotCount));
                          } else {
                            return streamColumnIdentifiers(maybeFirstIncompleteSlot.get())
                                .thenApply(
                                    dataColumnIdentifierStream ->
                                        Pair.of(dataColumnIdentifierStream.count(), newSlotCount));
                          }
                        })
                    .thenPeek(
                        oldCountNewCount ->
                            LOG.info(
                                "[nyota] DataColumnSidecarDB: setFirstIncompleteSlot {} ({} cols) ~> {} ({} cols)",
                                maybeFirstIncompleteSlot.map(UInt64::toString).orElse("NA"),
                                oldCountNewCount.left(),
                                slot,
                                oldCountNewCount.right()))
                    .ifExceptionGetsHereRaiseABug();
              }
            });
  }

  @Override
  public void addSidecar(final DataColumnSidecar sidecar) {
    sidecarUpdateChannel.onNewSidecar(sidecar);
    addCounter.incrementAndGet();
    int slot = sidecar.getSlot().intValue();
    synchronized (this) {
      if (slot > maxAddedSlot) {
        final SafeFuture<UInt64> prevSlotCount =
            streamColumnIdentifiers(UInt64.valueOf(maxAddedSlot))
                .thenApply(
                    dataColumnIdentifierStream ->
                        UInt64.valueOf(dataColumnIdentifierStream.count()));
        final SafeFuture<UInt64> finalizedSlot =
            getFirstIncompleteSlot()
                .thenApply(
                    maybeFirstIncompleteSlot ->
                        maybeFirstIncompleteSlot.orElse(UInt64.ONE).decrement());
        SafeFuture.collectAll(prevSlotCount, finalizedSlot)
            .thenPeek(
                prevSlotCountFinalizedSlot -> {
                  LOG.info(
                      "[nyota] DataColumnSidecarDB.addSidecar: new slot: {}, prevSlot count: {}, total added: {}, finalizedSlot: {}",
                      slot,
                      prevSlotCountFinalizedSlot.get(0),
                      addCounter.get(),
                      prevSlotCountFinalizedSlot.get(1));
                })
            .ifExceptionGetsHereRaiseABug();
        maxAddedSlot = slot;
      }
    }
  }

  @Override
  public void pruneAllSidecars(final UInt64 tillSlotExclusive) {
    sidecarUpdateChannel.onSidecarsAvailabilitySlot(tillSlotExclusive);
  }
}
