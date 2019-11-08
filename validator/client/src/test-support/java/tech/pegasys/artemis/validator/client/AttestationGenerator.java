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

package tech.pegasys.artemis.validator.client;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class AttestationGenerator {
  private final List<BLSKeyPair> validatorKeys;
  private final BLSKeyPair randomKeyPair = BLSKeyPair.random();

  public AttestationGenerator(final List<BLSKeyPair> validatorKeys) {
    this.validatorKeys = validatorKeys;
  }

  public Attestation validAttestation(final ChainStorageClient storageClient) {
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    BeaconBlock block = storageClient.getStore().getBlock(bestBlockRoot);
    BeaconState state = storageClient.getStore().getBlockState(bestBlockRoot);
    return createAttestation(block, state, true);
  }

  public Attestation attestationWithInvalidSignature(final ChainStorageClient storageClient) {
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    BeaconBlock block = storageClient.getStore().getBlock(bestBlockRoot);
    BeaconState state = storageClient.getStore().getBlockState(bestBlockRoot);
    return createAttestation(block, state, false);
  }

  private Attestation createAttestation(
      final BeaconBlock block, final BeaconState state, final boolean withValidSignature) {
    final UnsignedLong epoch = compute_epoch_at_slot(state.getSlot());
    HashMap<UnsignedLong, List<Triple<List<Integer>, UnsignedLong, Integer>>> committeeAssignments =
        new HashMap<>();
    int validatorIndex;
    for (validatorIndex = 0; validatorIndex < validatorKeys.size(); validatorIndex++) {
      final Optional<CommitteeAssignment> maybeAssignment =
          ValidatorClientUtil.get_committee_assignment(state, epoch, validatorIndex);
      if (maybeAssignment.isPresent()) {
        UnsignedLong slot = maybeAssignment.get().getSlot();
        final Triple<List<Integer>, UnsignedLong, Integer> committeeAssignment =
            new MutableTriple<>(
                maybeAssignment.get().getCommittee(), // List of validator indices in this committee
                maybeAssignment.get().getCommitteeIndex(), // Assigned shard
                validatorIndex // The index of the current validator
                );
        committeeAssignments.put(slot, List.of(committeeAssignment));
        break;
      }
    }
    if (committeeAssignments.isEmpty()) {
      throw new IllegalStateException("Unable to find committee assignment among validators");
    }

    final UnsignedLong slot = committeeAssignments.keySet().iterator().next();
    Triple<BLSPublicKey, Integer, Committee> attesterInfo =
        AttestationUtil.getAttesterInformation(state, committeeAssignments, slot).get(0);
    AttestationData genericAttestationData =
        AttestationUtil.getGenericAttestationData(state, block, slot);

    final BLSKeyPair validatorKeyPair =
        withValidSignature ? validatorKeys.get(validatorIndex) : randomKeyPair;
    return createAttestation(
        state,
        validatorKeyPair,
        attesterInfo.getMiddle(),
        attesterInfo.getRight(),
        genericAttestationData);
  }

  private Attestation createAttestation(
      BeaconState state,
      BLSKeyPair attesterKeyPair,
      int indexIntoCommittee,
      Committee committee,
      AttestationData genericAttestationData) {
    int commmitteSize = committee.getCommitteeSize();
    Bitlist aggregationBitfield =
        AttestationUtil.getAggregationBits(commmitteSize, indexIntoCommittee);
    Bitlist custodyBits = new Bitlist(commmitteSize, MAX_VALIDATORS_PER_COMMITTEE);
    AttestationData attestationData =
        AttestationUtil.completeAttestationCrosslinkData(
            state, new AttestationData(genericAttestationData), committee);
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    Bytes domain =
        get_domain(state, DOMAIN_BEACON_ATTESTER, attestationData.getTarget().getEpoch());

    BLSSignature signature = BLSSignature.sign(attesterKeyPair, attestationMessage, domain);
    return new Attestation(aggregationBitfield, attestationData, custodyBits, signature);
  }
}
