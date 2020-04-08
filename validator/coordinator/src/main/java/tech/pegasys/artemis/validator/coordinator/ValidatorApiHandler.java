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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_committee_count_at_slot;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.getAggregatorModulo;
import static tech.pegasys.artemis.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.eventbus.EventBus;
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
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.CommitteeAssignmentUtil;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.CommitteeAssignment;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.CommitteeUtil;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.networking.eth2.gossip.AttestationTopicSubscriptions;
import tech.pegasys.artemis.ssz.SSZTypes.Bitlist;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.artemis.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.artemis.storage.client.CombinedChainDataClient;
import tech.pegasys.artemis.util.async.ExceptionThrowingFunction;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.config.FeatureToggles;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.api.ValidatorDuties;

public class ValidatorApiHandler implements ValidatorApiChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final CombinedChainDataClient combinedChainDataClient;
  private final BlockFactory blockFactory;
  private final AggregatingAttestationPool attestationPool;
  private final AttestationAggregator attestationAggregator;
  private final AttestationTopicSubscriptions attestationTopicSubscriptions;
  private final EventBus eventBus;

  public ValidatorApiHandler(
      final CombinedChainDataClient combinedChainDataClient,
      final BlockFactory blockFactory,
      final AggregatingAttestationPool attestationPool,
      final AttestationAggregator attestationAggregator,
      final AttestationTopicSubscriptions attestationTopicSubscriptions,
      final EventBus eventBus) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.blockFactory = blockFactory;
    this.attestationPool = attestationPool;
    this.attestationAggregator = attestationAggregator;
    this.attestationTopicSubscriptions = attestationTopicSubscriptions;
    this.eventBus = eventBus;
  }

  @Override
  public SafeFuture<Optional<Fork>> getFork() {
    return SafeFuture.completedFuture(
        combinedChainDataClient.getHeadStateFromStore().map(BeaconState::getFork));
  }

  @Override
  public SafeFuture<Optional<List<ValidatorDuties>>> getDuties(
      final UnsignedLong epoch, final Collection<BLSPublicKey> publicKeys) {
    final UnsignedLong slot =
        compute_start_slot_at_epoch(
            epoch.compareTo(UnsignedLong.ZERO) > 0 ? epoch.minus(UnsignedLong.ONE) : epoch);
    LOG.trace("Retrieving duties from epoch {} using state at slot {}", epoch, slot);
    return combinedChainDataClient
        .getStateAtSlot(slot)
        .thenApply(
            optionalState ->
                optionalState.map(state -> getValidatorDutiesFromState(state, epoch, publicKeys)));
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
    final Optional<Bytes32> headRoot = combinedChainDataClient.getBestBlockRoot();
    if (headRoot.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final UnsignedLong bestSlot = combinedChainDataClient.getBestSlot();

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

  @Override
  public SafeFuture<Optional<Attestation>> createUnsignedAttestation(
      final UnsignedLong slot, final int committeeIndex) {
    return createFromBlockAndState(
        slot,
        blockAndState -> {
          final BeaconState state = blockAndState.getState();
          final BeaconBlock block = blockAndState.getBlock();
          final int committeeCount = get_committee_count_at_slot(state, slot).intValue();

          if (committeeIndex < 0 || committeeIndex >= committeeCount) {
            throw new IllegalArgumentException(
                "Invalid committee index "
                    + committeeIndex
                    + " - expected between 0 and "
                    + (committeeCount - 1));
          }
          final UnsignedLong committeeIndexUnsigned = UnsignedLong.valueOf(committeeIndex);
          final AttestationData attestationData =
              AttestationUtil.getGenericAttestationData(slot, state, block, committeeIndexUnsigned);
          final List<Integer> committee =
              CommitteeUtil.get_beacon_committee(state, slot, committeeIndexUnsigned);

          final Bitlist aggregationBits =
              new Bitlist(committee.size(), MAX_VALIDATORS_PER_COMMITTEE);
          return new Attestation(aggregationBits, attestationData, BLSSignature.empty());
        });
  }

  @Override
  public SafeFuture<Optional<Attestation>> createAggregate(final AttestationData attestationData) {
    return SafeFuture.completedFuture(attestationPool.createAggregateFor(attestationData));
  }

  @Override
  public void subscribeToBeaconCommittee(
      final int committeeIndex, final UnsignedLong aggregationSlot) {
    attestationTopicSubscriptions.subscribeToCommittee(committeeIndex, aggregationSlot);
  }

  @Override
  public void sendSignedAttestation(final Attestation attestation) {
    attestationPool.add(attestation);
    if (!FeatureToggles.USE_VALIDATOR_CLIENT_SERVICE) {
      attestationAggregator.addOwnValidatorAttestation(attestation);
    }
    eventBus.post(attestation);
  }

  @Override
  public void sendAggregateAndProof(final AggregateAndProof aggregateAndProof) {
    attestationPool.add(aggregateAndProof.getAggregate());
    eventBus.post(aggregateAndProof);
  }

  @Override
  public void sendSignedBlock(final SignedBeaconBlock block) {
    eventBus.post(new ProposedBlockEvent(block));
  }

  private List<ValidatorDuties> getValidatorDutiesFromState(
      final BeaconState state,
      final UnsignedLong epoch,
      final Collection<BLSPublicKey> publicKeys) {
    final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex =
        getBeaconProposalSlotsByValidatorIndex(state, epoch);
    return publicKeys.stream()
        .map(key -> getDutiesForValidator(key, state, epoch, proposalSlotsByValidatorIndex))
        .collect(toList());
  }

  private ValidatorDuties getDutiesForValidator(
      final BLSPublicKey key,
      final BeaconState state,
      final UnsignedLong epoch,
      final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex) {
    return ValidatorsUtil.getValidatorIndex(state, key)
        .map(
            index -> createValidatorDuties(proposalSlotsByValidatorIndex, key, state, epoch, index))
        .orElseGet(() -> ValidatorDuties.noDuties(key));
  }

  private ValidatorDuties createValidatorDuties(
      final Map<Integer, List<UnsignedLong>> proposalSlotsByValidatorIndex,
      final BLSPublicKey key,
      final BeaconState state,
      final UnsignedLong epoch,
      final Integer validatorIndex) {
    final List<UnsignedLong> proposerSlots =
        proposalSlotsByValidatorIndex.getOrDefault(validatorIndex, emptyList());
    final CommitteeAssignment committeeAssignment =
        CommitteeAssignmentUtil.get_committee_assignment(state, epoch, validatorIndex)
            .orElseThrow();
    return ValidatorDuties.withDuties(
        key,
        validatorIndex,
        Math.toIntExact(committeeAssignment.getCommitteeIndex().longValue()),
        committeeAssignment.getCommittee().indexOf(validatorIndex),
        getAggregatorModulo(committeeAssignment.getCommittee().size()),
        proposerSlots,
        committeeAssignment.getSlot());
  }

  private Map<Integer, List<UnsignedLong>> getBeaconProposalSlotsByValidatorIndex(
      final BeaconState state, final UnsignedLong epoch) {
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
