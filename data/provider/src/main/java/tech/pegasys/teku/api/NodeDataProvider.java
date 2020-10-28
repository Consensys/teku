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
import java.util.stream.Collectors;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;

public class NodeDataProvider {

  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;

  public NodeDataProvider(
      AggregatingAttestationPool attestationPool,
      OperationPool<AttesterSlashing> attesterSlashingsPool,
      OperationPool<ProposerSlashing> proposerSlashingPool,
      OperationPool<SignedVoluntaryExit> voluntaryExitPool) {
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingsPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
  }

  public List<tech.pegasys.teku.api.schema.AttesterSlashing> getAttesterSlashings() {
    return attesterSlashingPool.getAll().stream()
        .map(tech.pegasys.teku.api.schema.AttesterSlashing::new)
        .collect(Collectors.toList());
  }

  public List<tech.pegasys.teku.api.schema.ProposerSlashing> getProposerSlashings() {
    return proposerSlashingPool.getAll().stream()
        .map(tech.pegasys.teku.api.schema.ProposerSlashing::new)
        .collect(Collectors.toList());
  }

  public List<tech.pegasys.teku.api.schema.SignedVoluntaryExit> getVoluntaryExits() {
    return voluntaryExitPool.getAll().stream()
        .map(tech.pegasys.teku.api.schema.SignedVoluntaryExit::new)
        .collect(Collectors.toList());
  }

  public int getAttestationPoolSize() {
    return attestationPool.getSize();
  }
}
