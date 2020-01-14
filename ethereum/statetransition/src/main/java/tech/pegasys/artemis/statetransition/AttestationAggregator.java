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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.getAttesterIndexIntoCommittee;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.representsNewAttester;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.validator.AggregatorInformation;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.bls.BLSAggregate;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.Constants;

public class AttestationAggregator {

  private final ConcurrentHashMap<Bytes32, Attestation> dataHashToAggregate =
      new ConcurrentHashMap<>();

  @VisibleForTesting
  final Map<UnsignedLong, AggregatorInformation> committeeIndexToAggregatorInformation =
      new ConcurrentHashMap<>();

  private final Map<UnsignedLong, Attestation> committeeIndexToAggregate =
      new ConcurrentHashMap<>();

  public void updateAggregatorInformations(List<AttesterInformation> attesterInformations) {

    attesterInformations.forEach(
        attester ->
            attester
                .getSelection_proof()
                .ifPresent(
                    selection_proof -> {
                      UnsignedLong committeeIndex = attester.getCommittee().getIndex();
                      committeeIndexToAggregatorInformation.put(
                          committeeIndex,
                          new AggregatorInformation(selection_proof, attester.getValidatorIndex()));
                    }));
  }

  public void addOwnValidatorAttestation(Attestation newAttestation) {
    Bytes32 attestationDataHashTreeRoot = newAttestation.getData().hash_tree_root();
    AtomicBoolean isNewData = new AtomicBoolean(false);
    Attestation aggregateAttestation =
        dataHashToAggregate.computeIfAbsent(
            attestationDataHashTreeRoot,
            (key) -> {
              isNewData.set(true);
              return newAttestation;
            });

    // If there exists an old aggregate attestation with the same Attestation Data,
    // and the new Attestation represents a new attester, add the signature of the
    // new attestation to the old aggregate attestation.
    if (!isNewData.get() && representsNewAttester(aggregateAttestation, newAttestation)) {
      aggregateAttestations(aggregateAttestation, newAttestation);
    }

    // If the attestation message hasn't been seen before:
    // - add it to the aggregate attestation map to aggregate further when
    // another attestation with the same message is received
    // - add it to the list of aggregate attestations for that commiteeeIndex
    // to broadcast
    else if (isNewData.get()) {
      UnsignedLong committeeIndex = newAttestation.getData().getIndex();
      committeeIndexToAggregate.put(committeeIndex, newAttestation);
    }
  }

  public void processAttestation(Attestation newAttestation) {

    Bytes32 attestationDataHashTreeRoot = newAttestation.getData().hash_tree_root();
    dataHashToAggregate.computeIfPresent(
        attestationDataHashTreeRoot,
        (root, attestation) -> {
          if (representsNewAttester(attestation, newAttestation)) {
            attestation.getAggregation_bits().setBit(getAttesterIndexIntoCommittee(newAttestation));

            aggregateAttestations(attestation, newAttestation);
          }
          return attestation;
        });
  }

  private synchronized void aggregateAttestations(
      Attestation oldAggregateAttestation, Attestation newAttestation) {

    // Set the bit of the new attester in the aggregate attestation
    oldAggregateAttestation
        .getAggregation_bits()
        .setBit(getAttesterIndexIntoCommittee(newAttestation));

    List<BLSSignature> signaturesToAggregate = new ArrayList<>();
    signaturesToAggregate.add(oldAggregateAttestation.getAggregate_signature());
    signaturesToAggregate.add(newAttestation.getAggregate_signature());
    oldAggregateAttestation.setAggregate_signature(
        BLSAggregate.bls_aggregate_signatures(signaturesToAggregate));
  }

  /**
   * Groups passed attestations by their {@link
   * tech.pegasys.artemis.datastructures.operations.AttestationData} and aggregates attestations in
   * every group to a single {@link Attestation}
   *
   * @return a list of aggregated {@link Attestation}s with distinct {@link
   *     tech.pegasys.artemis.datastructures.operations.AttestationData}
   */
  public static List<Attestation> groupAndAggregateAttestations(List<Attestation> srcAttestations) {
    Collection<List<Attestation>> groupedAtt =
        srcAttestations.stream().collect(Collectors.groupingBy(Attestation::getData)).values();
    return groupedAtt.stream()
        .map(AttestationAggregator::aggregateAttestations)
        .collect(Collectors.toList());
  }

  /**
   * Aggregates passed attestations
   *
   * @param srcAttestations attestations which should have the same {@link Attestation#getData()}
   */
  public static Attestation aggregateAttestations(List<Attestation> srcAttestations) {
    assert !srcAttestations.isEmpty();
    assert srcAttestations.stream()
        .skip(1)
        .allMatch(a -> a.getData().equals(srcAttestations.get(0).getData()));

    int targetBitlistSize =
        srcAttestations.stream()
            .mapToInt(a -> a.getAggregation_bits().getCurrentSize())
            .max()
            .getAsInt();
    Bitlist targetBitlist = new Bitlist(targetBitlistSize, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    srcAttestations.forEach(a -> targetBitlist.setAllBits(a.getAggregation_bits()));
    BLSSignature targetSig =
        BLSAggregate.bls_aggregate_signatures(
            srcAttestations.stream()
                .map(Attestation::getAggregate_signature)
                .collect(Collectors.toList()));

    return new Attestation(targetBitlist, srcAttestations.get(0).getData(), targetSig);
  }

  public void reset() {
    dataHashToAggregate.clear();
    committeeIndexToAggregatorInformation.clear();
    committeeIndexToAggregate.clear();
  }

  public synchronized List<AggregateAndProof> getAggregateAndProofs() {
    List<AggregateAndProof> aggregateAndProofs = new ArrayList<>();
    for (UnsignedLong commiteeIndex : committeeIndexToAggregatorInformation.keySet()) {
      AggregatorInformation aggregatorInformation =
          committeeIndexToAggregatorInformation.get(commiteeIndex);
      Attestation aggregate = committeeIndexToAggregate.get(commiteeIndex);
      aggregateAndProofs.add(
          new AggregateAndProof(
              UnsignedLong.valueOf(aggregatorInformation.getValidatorIndex()),
              aggregatorInformation.getSelection_proof(),
              aggregate));
    }
    return aggregateAndProofs;
  }
}
