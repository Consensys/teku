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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeMessageValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<UniquenessKey> seenIndices =
      LimitedSet.createSynchronizedIterable(VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE * 2);
  private final Spec spec;
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final SyncCommitteeCurrentSlotUtil slotUtil;

  private final RecentChainData recentChainData;

  public SyncCommitteeMessageValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final TimeProvider timeProvider) {
    this.spec = spec;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
    this.signatureVerifier = signatureVerifier;
    this.slotUtil = new SyncCommitteeCurrentSlotUtil(recentChainData, spec, timeProvider);
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidatableSyncCommitteeMessage validateableMessage) {

    final SyncCommitteeMessage message = validateableMessage.getMessage();

    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil =
        spec.getSyncCommitteeUtil(message.getSlot());
    if (maybeSyncCommitteeUtil.isEmpty()) {
      return completedFuture(
          reject(
              "Rejecting sync committee message because the fork active at slot %s does not support sync committees",
              message.getSlot()));
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();

    // [IGNORE] The message's slot is for the current slot(with a MAXIMUM_GOSSIP_CLOCK_DISPARITY
    // allowance),
    // i.e. sync_committee_message.slot == current_slot.
    if (!slotUtil.isForCurrentSlot(message.getSlot())) {
      LOG.trace(
          "Ignoring sync committee message from validator {}, "
              + "because it is not from the current slot "
              + "(message slot: {}, current slot: {})",
          message::getValidatorIndex,
          message::getSlot,
          recentChainData::getCurrentSlot);
      return completedFuture(IGNORE);
    }

    // [IGNORE] There has been no other valid sync committee message for the declared slot for the
    // validator referenced by sync_committee_message.validator_index, unless the
    // block being signed (beacon_block_root) matches the local head as selected by fork
    // choice (this requires maintaining a cache of size `SYNC_COMMITTEE_SIZE //
    // SYNC_COMMITTEE_SUBNET_COUNT` for each subnet that can be flushed after each slot).
    // Note this validation is _per topic_ so that for a given `slot`, multiple messages could be
    // forwarded with the same `validator_index` as long as the `subnet_id`s are distinct.
    final Optional<UniquenessKey> uniquenessKey;
    if (validateableMessage.getReceivedSubnetId().isPresent()) {
      final UniquenessKey key =
          getUniquenessKey(
              message,
              validateableMessage.getReceivedSubnetId().getAsInt(),
              message.getBeaconBlockRoot());
      final Optional<Bytes32> maybeBestBlockRoot = recentChainData.getBestBlockRoot();

      if (maybeBestBlockRoot.isPresent()) {
        final Optional<Bytes32> bestSeenRoot =
            seenIndices.stream()
                .filter(item -> item.isSameIgnoringBlockRoot(key))
                .findFirst()
                .map(UniquenessKey::getBlockRoot);
        // I've already seen this message, can ignore it.
        if (bestSeenRoot.isPresent() && maybeBestBlockRoot.get().equals(bestSeenRoot.get())) {
          return completedFuture(IGNORE);
        } else {
          if (seenIndices.remove(key)) {
            // I've seen a message for this slot already, and its block root didn't match current
            // head
            LOG.trace(
                "Removed already seen sync committee message from cache "
                    + "to accept a better one for validator index {}, slot {}",
                message.getValidatorIndex(),
                message.getSlot());
          }
        }
      }
      uniquenessKey = Optional.of(key);
    } else {
      uniquenessKey = Optional.empty();
    }

    return syncCommitteeStateUtils
        .getStateForSyncCommittee(message.getSlot())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "Ignoring sync committee message because state is not available or not from Altair fork");
                return completedFuture(IGNORE);
              }
              final BeaconStateAltair state = maybeState.get();
              return validateWithState(
                  validateableMessage, message, syncCommitteeUtil, state, uniquenessKey);
            });
  }

  private SafeFuture<InternalValidationResult> validateWithState(
      final ValidatableSyncCommitteeMessage validateableMessage,
      final SyncCommitteeMessage message,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAltair state,
      final Optional<UniquenessKey> maybeUniquenessKey) {
    final UInt64 messageEpoch = spec.computeEpochAtSlot(message.getSlot());

    // Always calculate the applicable subcommittees to ensure they are cached and can be used to
    // send the gossip.
    final SyncSubcommitteeAssignments assignedSubcommittees =
        validateableMessage.calculateAssignments(spec, state);

    // [REJECT] The validator producing this sync_committee_message is in the current sync
    // committee, i.e. state.validators[sync_committee_message.validator_index].pubkey in
    // state.current_sync_committee.pubkeys.
    if (assignedSubcommittees.isEmpty()) {
      return completedFuture(
          reject(
              "Rejecting sync committee message because validator is not in the sync committee"));
    }

    // For messages received via gossip, it has to be unique based on the subnet it was on
    // For locally produced messages we should accept it if it hasn't been seen on any subnet
    final List<UniquenessKey> uniquenessKeys =
        maybeUniquenessKey
            .map(List::of)
            .orElseGet(
                () ->
                    assignedSubcommittees
                        .getAssignedSubcommittees()
                        .intStream()
                        .mapToObj(
                            subnetId ->
                                getUniquenessKey(message, subnetId, message.getBeaconBlockRoot()))
                        .collect(toList()));

    // [IGNORE] There has been no other valid sync committee message for the declared slot for the
    // validator referenced by sync_committee_message.validator_index.
    if (seenIndices.containsAll(uniquenessKeys)) {
      return completedFuture(IGNORE);
    }

    // [REJECT] The subnet_id is correct, i.e. subnet_id in
    // compute_subnets_for_sync_committee(state, sync_committee_message.validator_index).
    if (validateableMessage.getReceivedSubnetId().isPresent()
        && !assignedSubcommittees
            .getAssignedSubcommittees()
            .contains(validateableMessage.getReceivedSubnetId().getAsInt())) {
      return completedFuture(
          reject("Rejecting sync committee message because subnet id is incorrect"));
    }

    final Optional<BLSPublicKey> maybeValidatorPublicKey =
        spec.getValidatorPubKey(state, message.getValidatorIndex());
    if (maybeValidatorPublicKey.isEmpty()) {
      return completedFuture(
          reject("Rejecting sync committee message because the validator index is unknown"));
    }

    // [REJECT] The message is valid for the message beacon_block_root for the validator
    // referenced by validator_index.
    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(messageEpoch), state.getGenesisValidatorsRoot());
    final Bytes32 signingRoot =
        syncCommitteeUtil.getSyncCommitteeMessageSigningRoot(
            message.getBeaconBlockRoot(), messageEpoch, forkInfo);
    return signatureVerifier
        .verify(maybeValidatorPublicKey.get(), signingRoot, message.getSignature())
        .thenApply(
            signatureValid -> {
              if (!signatureValid) {
                return reject("Rejecting sync committee message because the signature is invalid");
              }
              if (!seenIndices.addAll(uniquenessKeys)) {
                return ignore(
                    "Ignoring sync committee message as a duplicate was processed during validation");
              }
              return ACCEPT;
            });
  }

  private UniquenessKey getUniquenessKey(
      final SyncCommitteeMessage message, final int subnetId, final Bytes32 root) {
    return new UniquenessKey(message.getValidatorIndex(), message.getSlot(), subnetId, root);
  }

  private static class UniquenessKey {
    private final UInt64 validatorIndex;
    private final UInt64 slot;
    private final int subnetId;

    private final Bytes32 blockRoot;

    private UniquenessKey(
        final UInt64 validatorIndex,
        final UInt64 slot,
        final int subnetId,
        final Bytes32 blockRoot) {
      this.validatorIndex = validatorIndex;
      this.slot = slot;
      this.subnetId = subnetId;
      this.blockRoot = blockRoot;
    }

    public Bytes32 getBlockRoot() {
      return blockRoot;
    }

    boolean isSameIgnoringBlockRoot(final UniquenessKey candidate) {
      return candidate.validatorIndex.equals(this.validatorIndex)
          && candidate.subnetId == this.subnetId
          && candidate.slot.equals(this.slot);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final UniquenessKey that = (UniquenessKey) o;
      return subnetId == that.subnetId
          && Objects.equals(validatorIndex, that.validatorIndex)
          && Objects.equals(slot, that.slot)
          && Objects.equals(blockRoot, that.blockRoot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(validatorIndex, slot, subnetId, blockRoot);
    }
  }
}
