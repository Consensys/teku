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

package tech.pegasys.artemis.validator.coordinator;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.statetransition.util.CommitteeAssignmentUtil;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.ExceptionThrowingFunction;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties;

public class ValidatorApiHandler implements ValidatorApiChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final CombinedChainDataClient combinedChainDataClient;
  private final BlockFactory blockFactory;

  public ValidatorApiHandler(
      final CombinedChainDataClient combinedChainDataClient, final BlockFactory blockFactory) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.blockFactory = blockFactory;
  }

  @Override
  public SafeFuture<List<ValidatorDuties>> getDuties(
      final UnsignedLong epoch, final Collection<BLSPublicKey> publicKeys) {
    final UnsignedLong slot = BeaconStateUtil.compute_start_slot_at_epoch(epoch);
    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(
            optionalState ->
                optionalState
                    .map(state -> getValidatorDutiesFromState(state, publicKeys))
                    .orElseGet(
                        () -> {
                          LOG.warn(
                              "Unable to calculate validator duties for epoch {} because state was unavailable",
                              epoch);
                          return emptyList();
                        }));
  }

  @Override
  public SafeFuture<Optional<BeaconBlock>> createUnsignedBlock(
      final UnsignedLong slot, final BLSSignature randaoReveal) {
    return createFromBlockAndState(
        slot.minus(UnsignedLong.ONE),
        blockAndState ->
            blockFactory.createUnsignedBlock(
                blockAndState.getState(), blockAndState.getBlock(), slot, randaoReveal));
  }

  private <T> SafeFuture<Optional<T>> createFromBlockAndState(
      final UnsignedLong maximumSlot,
      final ExceptionThrowingFunction<BeaconBlockAndState, T> creator) {
    final UnsignedLong bestSlot = combinedChainDataClient.getBestSlot();
    final Optional<Bytes32> headRoot = combinedChainDataClient.getBestBlockRoot();
    if (headRoot.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    // We need to request the block on the canonical chain which is strictly before slot
    // If slot is past the end of our canonical chain, we need the last block from our chain.
    final UnsignedLong parentBlockSlot =
        bestSlot.compareTo(maximumSlot) >= 0 ? maximumSlot : bestSlot;
    return combinedChainDataClient
        .getBlockAndStateInEffectAtSlot(parentBlockSlot, headRoot.get())
        .thenApplyChecked(
            maybeBlockAndState -> {
              if (maybeBlockAndState.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(creator.apply(maybeBlockAndState.get()));
            });
  }

  private List<ValidatorDuties> getValidatorDutiesFromState(
      final BeaconState state, final Collection<BLSPublicKey> publicKeys) {
    final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex =
        getBeaconProposalSlotsByValidatorIndex(state);
    return publicKeys.stream()
        .map(key -> getDutiesForValidator(key, state, proposalSlotsByValidatorIndex))
        .collect(toList());
  }

  private ValidatorDuties getDutiesForValidator(
      final BLSPublicKey key,
      final BeaconState state,
      final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex) {
    return ValidatorsUtil.getValidatorIndex(state, key)
        .map(index -> createValidatorDuties(proposalSlotsByValidatorIndex, key, state, index))
        .orElseGet(() -> ValidatorDuties.noDuties(key));
  }

  private ValidatorDuties createValidatorDuties(
      final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex,
      final BLSPublicKey key,
      final BeaconState state,
      final Integer validatorIndex) {
    final List<UnsignedLong> proposerSlots =
        proposalSlotsByValidatorIndex.getOrDefault(validatorIndex, emptyList());
    final CommitteeAssignment committeeAssignment =
        CommitteeAssignmentUtil.get_committee_assignment(
                state, compute_epoch_at_slot(state.getSlot()), validatorIndex)
            .orElseThrow();
    return ValidatorDuties.withDuties(
        key,
        validatorIndex,
        Math.toIntExact(committeeAssignment.getCommitteeIndex().longValue()),
        proposerSlots,
        committeeAssignment.getSlot());
  }

  private Map<Integer, List<UnsignedLong>> getBeaconProposalSlotsByValidatorIndex(
      final BeaconState state) {
    final UnsignedLong epoch = compute_epoch_at_slot(state.getSlot());
    final UnsignedLong startSlot = compute_start_slot_at_epoch(epoch);
    final UnsignedLong endSlot = startSlot.plus(UnsignedLong.valueOf(Constants.SLOTS_PER_EPOCH));
    final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex = new HashMap<>();
    for (UnsignedLong slot = startSlot;
        slot.compareTo(endSlot) < 0;
        slot = slot.plus(UnsignedLong.ONE)) {
      final Integer proposer = get_beacon_proposer_index(state, slot);
      proposalSlotsByValidatorIndex.computeIfAbsent(proposer, key -> new ArrayList<>()).add(slot);
    }
    return proposalSlotsByValidatorIndex;
  }
}
