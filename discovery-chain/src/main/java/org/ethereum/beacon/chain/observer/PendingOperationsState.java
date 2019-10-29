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

package org.ethereum.beacon.chain.observer;

import static java.util.stream.Collectors.groupingBy;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.Transfer;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.attestation.AttestationData;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.crypto.BLS381;
import tech.pegasys.artemis.util.collections.Bitlist;

public class PendingOperationsState implements PendingOperations {

  private final List<Attestation> attestations;

  public PendingOperationsState(List<Attestation> attestations) {
    this.attestations = attestations;
  }

  @Override
  public List<Attestation> getAttestations() {
    return attestations;
  }

  @Override
  public List<ProposerSlashing> peekProposerSlashings(int maxCount) {
    return Collections.emptyList();
  }

  @Override
  public List<AttesterSlashing> peekAttesterSlashings(int maxCount) {
    return Collections.emptyList();
  }

  @Override
  public List<Attestation> peekAggregateAttestations(int maxCount, SpecConstants specConstants) {
    Map<AttestationData, List<Attestation>> attestationsBySlot =
        attestations.stream().collect(groupingBy(Attestation::getData));
    return attestationsBySlot.entrySet().stream()
        .sorted(Comparator.comparing(e -> e.getKey().getTarget().getEpoch()))
        .map(entry -> aggregateAttestations(entry.getValue(), specConstants))
        .limit(maxCount)
        .collect(Collectors.toList());
  }

  private Attestation aggregateAttestations(
      List<Attestation> attestations, SpecConstants specConstants) {
    assert !attestations.isEmpty();
    assert attestations.stream()
        .skip(1)
        .allMatch(a -> a.getData().equals(attestations.get(0).getData()));

    Bitlist participants =
        attestations.stream().map(Attestation::getAggregationBits).reduce(Bitlist::or).get();
    Bitlist custody =
        attestations.stream().map(Attestation::getCustodyBits).reduce(Bitlist::or).get();
    BLS381.Signature aggregatedSignature =
        BLS381.Signature.aggregate(
            attestations.stream()
                .map(Attestation::getSignature)
                .map(BLS381.Signature::create)
                .collect(Collectors.toList()));
    BLSSignature aggSign = BLSSignature.wrap(aggregatedSignature.getEncoded());

    return new Attestation(
        participants, attestations.get(0).getData(), custody, aggSign, specConstants);
  }

  @Override
  public List<VoluntaryExit> peekExits(int maxCount) {
    return Collections.emptyList();
  }

  @Override
  public List<Transfer> peekTransfers(int maxCount) {
    return Collections.emptyList();
  }
}
