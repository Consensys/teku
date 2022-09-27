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

package tech.pegasys.teku.statetransition.synccommittee;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SyncCommitteeMessagePool implements SlotEventsChannel {

  private final Subscribers<OperationAddedSubscriber<ValidateableSyncCommitteeMessage>>
      subscribers = Subscribers.create(true);

  private final Spec spec;
  private final SyncCommitteeMessageValidator validator;
  /**
   * Effectively provides a mapping from (slot, blockRoot, subcommitteeIndex) -> ContributionData
   * but using a nested map under slot so that pruning based on slot is efficient.
   */
  private final NavigableMap<UInt64, Map<BlockRootAndCommitteeIndex, ContributionData>>
      committeeContributionData = new TreeMap<>();

  private final MetricsCountersByIntervals metricsCountersByIntervals;

  public SyncCommitteeMessagePool(
      final Spec spec,
      final SyncCommitteeMessageValidator validator,
      final MetricsSystem metricsSystem) {
    this.spec = spec;
    this.validator = validator;
    metricsCountersByIntervals =
        MetricsCountersByIntervals.create(
            TekuMetricCategory.BEACON,
            metricsSystem,
            "sync_committee_validation_perf",
            "sync_committee_validation_perf",
            Collections.emptyList(),
            Map.of(List.of(), List.of(25L, 50L, 100L, 200L, 500L)));
  }

  public void subscribeOperationAdded(
      OperationAddedSubscriber<ValidateableSyncCommitteeMessage> subscriber) {
    subscribers.subscribe(subscriber);
  }

  public SafeFuture<InternalValidationResult> addLocal(
      final ValidateableSyncCommitteeMessage message) {
    return add(message, false);
  }

  public SafeFuture<InternalValidationResult> addRemote(
      final ValidateableSyncCommitteeMessage message) {
    return add(message, true);
  }

  private SafeFuture<InternalValidationResult> add(
      final ValidateableSyncCommitteeMessage message, final boolean fromNetwork) {
    final long start = System.currentTimeMillis();
    return validator
        .validate(message)
        .thenPeek(
            result -> {
              final long time = System.currentTimeMillis() - start;
              metricsCountersByIntervals.recordValue(time);
              if (result.isAccept()) {
                subscribers.forEach(
                    subscriber -> subscriber.onOperationAdded(message, result, fromNetwork));
                doAdd(message);
              }
            });
  }

  private synchronized void doAdd(final ValidateableSyncCommitteeMessage message) {
    final SyncSubcommitteeAssignments assignments =
        message.getSubcommitteeAssignments().orElseThrow();
    final Map<BlockRootAndCommitteeIndex, ContributionData> blockRootAndCommitteeIndexToMessages =
        committeeContributionData.computeIfAbsent(message.getSlot(), __ -> new HashMap<>());
    final IntSet applicableSubnets;
    if (message.getReceivedSubnetId().isEmpty()) {
      applicableSubnets = assignments.getAssignedSubcommittees();
    } else {
      applicableSubnets = IntSet.of(message.getReceivedSubnetId().getAsInt());
    }
    applicableSubnets.forEach(
        subcommitteeIndex ->
            blockRootAndCommitteeIndexToMessages
                .computeIfAbsent(
                    new BlockRootAndCommitteeIndex(message.getBeaconBlockRoot(), subcommitteeIndex),
                    __ -> new ContributionData())
                .add(
                    assignments.getParticipationBitIndices(subcommitteeIndex),
                    message.getMessage().getSignature()));
  }

  public synchronized Optional<SyncCommitteeContribution> createContribution(
      final UInt64 slot, final Bytes32 blockRoot, final int subcommitteeIndex) {
    return getContributionData(slot, blockRoot, subcommitteeIndex)
        .map(
            contributionData ->
                spec.getSyncCommitteeUtilRequired(slot)
                    .createSyncCommitteeContribution(
                        slot,
                        blockRoot,
                        UInt64.valueOf(subcommitteeIndex),
                        contributionData.getParticipationIndices(),
                        contributionData.getAggregatedSignature()));
  }

  /**
   * Prunes by removing all messages more than one slot old. Theoretically only the current slot
   * messages are required but we provide a one slot tolerance.
   *
   * @param slot the current node slot
   */
  @Override
  public synchronized void onSlot(final UInt64 slot) {
    committeeContributionData.headMap(slot.minusMinZero(1), false).clear();
  }

  private Optional<ContributionData> getContributionData(
      final UInt64 slot, final Bytes32 blockRoot, final int subcommitteeIndex) {
    return Optional.ofNullable(
        committeeContributionData
            .getOrDefault(slot, Collections.emptyMap())
            .get(new BlockRootAndCommitteeIndex(blockRoot, subcommitteeIndex)));
  }

  private static class BlockRootAndCommitteeIndex {
    private final Bytes32 blockRoot;
    private final int committeeIndex;

    private BlockRootAndCommitteeIndex(final Bytes32 blockRoot, final int committeeIndex) {
      this.blockRoot = blockRoot;
      this.committeeIndex = committeeIndex;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BlockRootAndCommitteeIndex that = (BlockRootAndCommitteeIndex) o;
      return committeeIndex == that.committeeIndex && Objects.equals(blockRoot, that.blockRoot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(blockRoot, committeeIndex);
    }
  }

  private static class ContributionData {
    private final IntSet participationIndices = new IntOpenHashSet();
    private final List<BLSSignature> signatures = new ArrayList<>();

    public void add(final IntSet participationIndices, final BLSSignature signature) {
      IntIterator iterator = participationIndices.iterator();
      while (iterator.hasNext()) {
        int index = iterator.nextInt();
        if (!this.participationIndices.add(index)) {
          throw new IllegalStateException("Already added " + index);
        }
        this.signatures.add(signature);
      }
    }

    public IntSet getParticipationIndices() {
      return participationIndices;
    }

    public BLSSignature getAggregatedSignature() {
      return signatures.isEmpty() ? BLSSignature.infinity() : BLS.aggregate(signatures);
    }
  }
}
