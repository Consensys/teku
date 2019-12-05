/*
 * Copyright 2019 ConsenSys AG.
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

import static java.lang.Math.toIntExact;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.bytes_to_int;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.COMMITTEE_INDEX_SUBSCRIPTION_LENGTH;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.TARGET_AGGREGATORS_PER_COMMITTEE;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.getSignature;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isEpochStart;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isGenesis;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.statetransition.CommitteeAssignment;
import tech.pegasys.artemis.statetransition.events.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.events.CommitteeAssignmentEvent;
import tech.pegasys.artemis.statetransition.events.CommitteeDismissalEvent;
import tech.pegasys.artemis.statetransition.util.CommitteeAssignmentUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.StoreInitializedEvent;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class CommitteeAssignmentManager {

  private Map<Integer, UnsignedLong> committeeIndexDeregisterEpoch = new HashMap<>();

  private final Map<BLSPublicKey, ValidatorInfo> validators;
  private final Map<UnsignedLong, List<AttesterInformation>> committeeAssignments;

  private final ChainStorageClient chainStorageClient;
  private final EventBus eventBus;

  public CommitteeAssignmentManager(
      ChainStorageClient chainStorageClient,
      Map<BLSPublicKey, ValidatorInfo> validators,
      Map<UnsignedLong, List<AttesterInformation>> commiteeAssignments,
      EventBus eventBus) {
    this.validators = validators;
    this.committeeAssignments = commiteeAssignments;
    this.chainStorageClient = chainStorageClient;
    this.eventBus = eventBus;
    this.eventBus.register(this);
  }

  @Subscribe
  public void onStoreInitializedEvent(final StoreInitializedEvent event) {
    final Store store = chainStorageClient.getStore();
    final Bytes32 head = chainStorageClient.getBestBlockRoot();
    final BeaconState genesisState = store.getBlockState(head);

    // Get validator indices of our own validators
    List<Validator> validatorRegistry = genesisState.getValidators();
    IntStream.range(0, validatorRegistry.size())
        .forEach(
            i -> {
              if (validators.containsKey(validatorRegistry.get(i).getPubkey())) {
                STDOUT.log(
                    Level.DEBUG,
                    "owned index = " + i + ": " + validatorRegistry.get(i).getPubkey());
                validators.get(validatorRegistry.get(i).getPubkey()).setValidatorIndex(i);
              }
            });

    // Update committee assignments and subscribe to required committee indices for the next 2
    // epochs
    UnsignedLong genesisEpoch = UnsignedLong.valueOf(GENESIS_EPOCH);
    Set<Integer> committeeIndices = updateCommitteeAssignments(genesisState, genesisEpoch);
    committeeIndices.addAll(
        updateCommitteeAssignments(genesisState, genesisEpoch.plus(UnsignedLong.ONE)));
    handleCommitteeIndexRegistrations(genesisEpoch, committeeIndices);
  }

  @Subscribe
  public void onAttestation(BroadcastAttestationEvent event) throws IllegalArgumentException {
    Store store = chainStorageClient.getStore();
    BeaconState headState = store.getBlockState(event.getHeadBlockRoot());
    UnsignedLong slot = event.getNodeSlot();

    // At the start of each epoch or at genesis, update attestation assignments
    // for all validators
    if (!isGenesis(slot) && isEpochStart(slot)) {
      UnsignedLong epoch = compute_epoch_at_slot(slot);
      // NOTE: we get commmittee assignments for NEXT epoch
      Set<Integer> committeeIndices =
          updateCommitteeAssignments(headState, epoch.plus(UnsignedLong.ONE));

      handleCommitteeIndexRegistrations(epoch, committeeIndices);
      handleCommitteeIndexDeregistrations(epoch);
    }
  }

  private void handleCommitteeIndexRegistrations(
      UnsignedLong current_epoch, Set<Integer> committeeIndices) {
    List<Integer> committeeIndicesToRegister = new ArrayList<>();

    committeeIndices.forEach(
        index -> {
          if (!committeeIndexDeregisterEpoch.containsKey(index)) {
            committeeIndicesToRegister.add(index);
          }
          committeeIndexDeregisterEpoch.put(
              index, current_epoch.plus(UnsignedLong.valueOf(COMMITTEE_INDEX_SUBSCRIPTION_LENGTH)));
        });

    if (!committeeIndicesToRegister.isEmpty()) {
      this.eventBus.post(new CommitteeAssignmentEvent(committeeIndicesToRegister));
    }
  }

  private void handleCommitteeIndexDeregistrations(UnsignedLong epoch) {
    List<Integer> committeeIndicesToDeregister = new ArrayList<>();
    for (Map.Entry<Integer, UnsignedLong> entry : committeeIndexDeregisterEpoch.entrySet()) {
      UnsignedLong deregisterEpoch = entry.getValue();
      int index = entry.getKey();
      if (epoch.compareTo(deregisterEpoch) > 0) {
        committeeIndicesToDeregister.add(index);
        committeeIndexDeregisterEpoch.remove(index);
      }
    }

    if (!committeeIndicesToDeregister.isEmpty()) {
      this.eventBus.post(new CommitteeDismissalEvent(committeeIndicesToDeregister));
    }
  }

  // Returns committee indices to subscribe according to the updated committee assignments
  private Set<Integer> updateCommitteeAssignments(BeaconState state, UnsignedLong epoch) {

    Set<Integer> committeeIndicesToSubscribe = new HashSet<>();

    // For each validator, using the spec defined get_committee_assignment,
    // get each validators committee assignment. i.e. learn to which
    // committee they belong in this epoch, and when that committee is
    // going to attest.
    validators.forEach(
        (pubKey, validatorInformation) -> {
          Optional<CommitteeAssignment> committeeAssignment =
              CommitteeAssignmentUtil.get_committee_assignment(
                  state, epoch, validatorInformation.getValidatorIndex());

          // If it exists, use the committee assignment information to update our
          // committeeAssignments map, which maps slots to Lists of AttesterInformation
          // objects, which contain all the information necessary to produce an attestation
          // for the given validator.
          committeeAssignment.ifPresent(
              assignment -> {
                UnsignedLong slot = assignment.getSlot();
                UnsignedLong committeeIndex = assignment.getCommitteeIndex();
                committeeIndicesToSubscribe.add(toIntExact(committeeIndex.longValue()));
                BLSSignature slot_signature = slot_signature(state, slot, pubKey);
                boolean is_aggregator = is_aggregator(state, slot, committeeIndex, slot_signature);

                List<AttesterInformation> attesterInformationInSlot =
                    committeeAssignments.computeIfAbsent(slot, k -> new ArrayList<>());

                List<Integer> indicesInCommittee = assignment.getCommittee();
                Committee committee = new Committee(committeeIndex, indicesInCommittee);
                int validatorIndex = validatorInformation.getValidatorIndex();
                int indexIntoCommittee = indicesInCommittee.indexOf(validatorIndex);

                attesterInformationInSlot.add(
                    new AttesterInformation(
                        validatorIndex,
                        pubKey,
                        indexIntoCommittee,
                        committee,
                        is_aggregator ? Optional.of(slot_signature) : Optional.empty()));
              });
        });

    return committeeIndicesToSubscribe;
  }

  public BLSSignature slot_signature(BeaconState state, UnsignedLong slot, BLSPublicKey signer) {
    Bytes domain = get_domain(state, DOMAIN_BEACON_ATTESTER, compute_epoch_at_slot(slot));
    Bytes32 slot_hash =
        HashTreeUtil.hash_tree_root(
            HashTreeUtil.SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue()));
    return getSignature(validators, slot_hash, domain, signer);
  }

  public boolean is_aggregator(
      BeaconState state,
      UnsignedLong slot,
      UnsignedLong committeeIndex,
      BLSSignature slot_signature) {
    List<Integer> committee = get_beacon_committee(state, slot, committeeIndex);
    UnsignedLong modulo =
        max(
            UnsignedLong.ONE,
            UnsignedLong.valueOf(committee.size()).dividedBy(TARGET_AGGREGATORS_PER_COMMITTEE));
    return (bytes_to_int(Hash.sha2_256(slot_signature.toBytes()).slice(0, 8)) % modulo.longValue())
        == 0;
  }
}
