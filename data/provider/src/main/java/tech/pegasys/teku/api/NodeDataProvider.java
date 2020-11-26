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

package tech.pegasys.teku.api;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class NodeDataProvider {

  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<tech.pegasys.teku.datastructures.operations.AttesterSlashing>
      attesterSlashingPool;
  private final OperationPool<tech.pegasys.teku.datastructures.operations.ProposerSlashing>
      proposerSlashingPool;
  private final OperationPool<tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit>
      voluntaryExitPool;

  public NodeDataProvider(
      AggregatingAttestationPool attestationPool,
      OperationPool<tech.pegasys.teku.datastructures.operations.AttesterSlashing>
          attesterSlashingsPool,
      OperationPool<tech.pegasys.teku.datastructures.operations.ProposerSlashing>
          proposerSlashingPool,
      OperationPool<tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit>
          voluntaryExitPool) {
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingsPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
  }

  public List<Attestation> getAttestations(
      Optional<UInt64> maybeSlot, Optional<UInt64> maybeCommitteeIndex) {
    return attestationPool
        .getAttestations(maybeSlot, maybeCommitteeIndex)
        .map(Attestation::new)
        .collect(Collectors.toList());
  }

  public List<AttesterSlashing> getAttesterSlashings() {
    return attesterSlashingPool.getAll().stream()
        .map(AttesterSlashing::new)
        .collect(Collectors.toList());
  }

  public List<ProposerSlashing> getProposerSlashings() {
    return proposerSlashingPool.getAll().stream()
        .map(ProposerSlashing::new)
        .collect(Collectors.toList());
  }

  public List<SignedVoluntaryExit> getVoluntaryExits() {
    return voluntaryExitPool.getAll().stream()
        .map(SignedVoluntaryExit::new)
        .collect(Collectors.toList());
  }

  public SafeFuture<InternalValidationResult> postVoluntaryExit(SignedVoluntaryExit exit) {
    return voluntaryExitPool.add(exit.asInternalSignedVoluntaryExit());
  }

  public SafeFuture<InternalValidationResult> postAttesterSlashing(AttesterSlashing slashing) {
    return attesterSlashingPool.add(slashing.asInternalAttesterSlashing());
  }

  public SafeFuture<InternalValidationResult> postProposerSlashing(ProposerSlashing slashing) {
    return proposerSlashingPool.add(slashing.asInternalProposerSlashing());
  }
}
