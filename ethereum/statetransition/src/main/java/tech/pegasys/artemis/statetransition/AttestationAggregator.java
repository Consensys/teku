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
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.isSingleAttester;
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.representsNewAttesterSingle;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.validator.AggregatorInformation;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.util.bls.BLSAggregate;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class AttestationAggregator {

  private final ConcurrentHashMap<Bytes32, Attestation> dataHashToAggregate =
      new ConcurrentHashMap<>();
  private final Map<UnsignedLong, AggregatorInformation> committeeIndexToAggregatorInformation =
      new ConcurrentHashMap<>();
  private final Map<UnsignedLong, List<Attestation>> committeeIndexToAggregatesList =
      new ConcurrentHashMap<>();
  private final AtomicBoolean aggregationActive = new AtomicBoolean(false);

  public void activateAggregation() {
    aggregationActive.set(true);
  }

  public void deactivateAggregation() {
    aggregationActive.set(false);
  }

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

  public void processAttestation(Attestation newAttestation) {
    if (!aggregationActive.get()) {
      // Make sure the new attestation only represents a single attester and that
      // one of our validators is the validator for attestation's committee index
      return;
    }

    if (!(isSingleAttester(newAttestation)
        && committeeIndexToAggregatorInformation
            .keySet()
            .contains(newAttestation.getData().getIndex()))) {
      return;
    }

    Bytes32 attestationDataHashTreeRoot = newAttestation.getData().hash_tree_root();
    AtomicBoolean isNewAggregate = new AtomicBoolean(false);
    Attestation aggregateAttestation =
        dataHashToAggregate.computeIfAbsent(
            attestationDataHashTreeRoot,
            (key) -> {
              isNewAggregate.set(true);
              return newAttestation;
            });

    // If there exists an old aggregate attestation with the same Attestation Data,
    // and the new Attestation represents a new attester, add the signature of the
    // new attestation to the old aggregate attestation.
    if (!isNewAggregate.get()
        && representsNewAttesterSingle(aggregateAttestation, newAttestation)) {

      // Set the bit of the new attester in the aggregate attestation
      aggregateAttestation
              .getAggregation_bits()
              .setBit(getAttesterIndexIntoCommittee(newAttestation));

      aggregateSignatures(aggregateAttestation, newAttestation);
    }

    // If the attestation message hasn't been seen before:
    // - add it to the aggregate attestation map to aggregate further when
    // another attestation with the same message is received
    // - add it to the list of aggregate attestations for that commiteeeIndex
    // to broadcast
    else if (isNewAggregate.get()) {
      UnsignedLong committeeIndex = newAttestation.getData().getIndex();
      committeeIndexToAggregatesList.computeIfAbsent(committeeIndex, k -> new ArrayList<>());
      List<Attestation> aggregatesList = committeeIndexToAggregatesList.get(committeeIndex);
      aggregatesList.add(newAttestation);
    }
  }

  private synchronized void aggregateSignatures(
      Attestation oldAggregateAttestation, Attestation newAttestation) {

    List<BLSSignature> signaturesToAggregate = new ArrayList<>();
    signaturesToAggregate.add(oldAggregateAttestation.getAggregate_signature());
    signaturesToAggregate.add(newAttestation.getAggregate_signature());
    oldAggregateAttestation.setAggregate_signature(
        BLSAggregate.bls_aggregate_signatures(signaturesToAggregate));
  }

  public void reset() {
    dataHashToAggregate.clear();
    committeeIndexToAggregatorInformation.clear();
    committeeIndexToAggregatesList.clear();
  }

  public synchronized List<AggregateAndProof> getAggregateAndProofs() {
    List<AggregateAndProof> aggregateAndProofs = new ArrayList<>();
    for (UnsignedLong commiteeIndex : committeeIndexToAggregatorInformation.keySet()) {
      AggregatorInformation aggregatorInformation =
          committeeIndexToAggregatorInformation.get(commiteeIndex);
      for (Attestation aggregate : committeeIndexToAggregatesList.get(commiteeIndex)) {
        aggregateAndProofs.add(
            new AggregateAndProof(
                UnsignedLong.valueOf(aggregatorInformation.getValidatorIndex()),
                aggregatorInformation.getSelection_proof(),
                aggregate));
      }
    }
    return aggregateAndProofs;
  }
}
