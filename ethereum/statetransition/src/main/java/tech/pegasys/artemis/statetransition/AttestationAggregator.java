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
import static tech.pegasys.artemis.datastructures.util.AttestationUtil.representsNewAttester;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.validator.AggregatorInformation;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.storage.ChainStorage;
import tech.pegasys.artemis.util.bls.BLSAggregate;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class AttestationAggregator {

  private ConcurrentHashMap<Bytes32, Attestation> dataHashToAggregate = new ConcurrentHashMap<>();
  private Map<UnsignedLong, AggregatorInformation> committeeIndexToAggregatorInformation =
      new ConcurrentHashMap<>();
  private Map<UnsignedLong, List<Attestation>> committeeIndexToAggregatesList =
      new ConcurrentHashMap<>();
  private AtomicBoolean aggregationActive = new AtomicBoolean(false);

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
    if (aggregationActive.get()) {
      // Make sure the new attestation only represents a single attester and that
      // one of our validators is the validator for attestation's committee index
      if (isSingleAttester(newAttestation)
          && committeeIndexToAggregatorInformation
              .keySet()
              .contains(newAttestation.getData().getIndex())) {

        Bytes32 attestationDataHashTreeRoot = newAttestation.getData().hash_tree_root();
        Optional<Attestation> aggregateAttestation =
            ChainStorage.get(attestationDataHashTreeRoot, dataHashToAggregate);

        // If there exists an old aggregate attestation with the same Attestation Data,
        // and the new Attestation represents a new attester, add the signature of the
        // new attestation to the old aggregate attestation.
        if (aggregateAttestation.isPresent()
            && representsNewAttester(aggregateAttestation.get(), newAttestation)) {

          aggregateAttestation(aggregateAttestation.get(), newAttestation);

        }

        // If the attestation message hasn't been seen before:
        // - add it to the aggregate attestation map to aggregate further when
        // another attestation with the same message is received
        // - add it to the list of aggregate attestations for that commiteeeIndex
        // to broadcast
        else if (aggregateAttestation.isEmpty()) {
          ChainStorage.add(attestationDataHashTreeRoot, newAttestation, dataHashToAggregate);
          UnsignedLong committeeIndex = newAttestation.getData().getIndex();
          committeeIndexToAggregatesList.computeIfAbsent(committeeIndex, k -> new ArrayList<>());
          List<Attestation> aggregatesList = committeeIndexToAggregatesList.get(committeeIndex);
          aggregatesList.add(newAttestation);
        }
      }
    }
  }

  private void aggregateAttestation(
      Attestation oldAggregateAttestation, Attestation newAttestation) {
    // Set the bit of the new attester in the aggregate attestation
    oldAggregateAttestation
        .getAggregation_bits()
        .setBit(getAttesterIndexIntoCommittee(newAttestation));

    // Aggregate signatures
    List<BLSSignature> signaturesToAggregate = new ArrayList<>();
    signaturesToAggregate.add(oldAggregateAttestation.getAggregate_signature());
    signaturesToAggregate.add(newAttestation.getAggregate_signature());
    oldAggregateAttestation.setAggregate_signature(
        BLSAggregate.bls_aggregate_signatures(signaturesToAggregate));
  }

  public void reset() {
    dataHashToAggregate = new ConcurrentHashMap<>();
    committeeIndexToAggregatorInformation = new ConcurrentHashMap<>();
    committeeIndexToAggregatesList = new ConcurrentHashMap<>();
  }

  public List<AggregateAndProof> getAggregateAndProofs() {
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
