/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidateableSyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SyncCommitteeSignaturePool implements SlotEventsChannel {

  private final Subscribers<OperationAddedSubscriber<ValidateableSyncCommitteeSignature>>
      subscribers = Subscribers.create(true);

  private final Spec spec;
  private final SyncCommitteeSignatureValidator validator;
  /**
   * Effectively provides a mapping from (slot, blockRoot, subcommitteeIndex) -> ContributionData
   * but using a nested map under slot so that pruning based on slot is efficient.
   */
  private final NavigableMap<UInt64, Map<BlockRootAndCommitteeIndex, ContributionData>>
      committeeContributionData = new TreeMap<>();

  public SyncCommitteeSignaturePool(
      final Spec spec, final SyncCommitteeSignatureValidator validator) {
    this.spec = spec;
    this.validator = validator;
  }

  public void subscribeOperationAdded(
      OperationAddedSubscriber<ValidateableSyncCommitteeSignature> subscriber) {
    subscribers.subscribe(subscriber);
  }

  public SafeFuture<InternalValidationResult> add(
      final ValidateableSyncCommitteeSignature signature) {
    return validator
        .validate(signature)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                subscribers.forEach(subscriber -> subscriber.onOperationAdded(signature, result));
                doAdd(signature);
              }
            });
  }

  private synchronized void doAdd(final ValidateableSyncCommitteeSignature signature) {
    final SyncSubcommitteeAssignments assignments =
        signature.getSubcommitteeAssignments().orElseThrow();
    final Map<BlockRootAndCommitteeIndex, ContributionData> blockRootAndCommitteeIndexToSignatures =
        committeeContributionData.computeIfAbsent(signature.getSlot(), __ -> new HashMap<>());
    assignments
        .getAssignedSubcommittees()
        .forEach(
            subcommitteeIndex ->
                blockRootAndCommitteeIndexToSignatures
                    .computeIfAbsent(
                        new BlockRootAndCommitteeIndex(
                            signature.getBeaconBlockRoot(), subcommitteeIndex),
                        __ -> new ContributionData())
                    .add(
                        assignments.getParticipationBitIndices(subcommitteeIndex),
                        signature.getSignature().getSignature()));
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
   * Prunes by removing all signatures more than one slot old. Theoretically only the current slot
   * signatures are required but we provide a one slot tolerance.
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
    private final Set<Integer> participationIndices = new HashSet<>();
    private final List<BLSSignature> signatures = new ArrayList<>();

    public void add(final Set<Integer> participationIndices, final BLSSignature signature) {
      this.participationIndices.addAll(participationIndices);
      this.signatures.add(signature);
    }

    public boolean isEmpty() {
      return signatures.isEmpty();
    }

    public Set<Integer> getParticipationIndices() {
      return participationIndices;
    }

    public BLSSignature getAggregatedSignature() {
      return signatures.isEmpty() ? BLSSignature.infinity() : BLS.aggregate(signatures);
    }
  }
}
