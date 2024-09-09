/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.operations.versions.phase0;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;

public class AttesterSlashingPhase0Schema
    extends ContainerSchema2<AttesterSlashingPhase0, IndexedAttestation, IndexedAttestation>
    implements AttesterSlashingSchema<AttesterSlashingPhase0> {
  public AttesterSlashingPhase0Schema(
      final IndexedAttestationSchema<IndexedAttestation> indexedAttestationSchema) {
    super(
        "AttesterSlashingPhase0",
        namedSchema("attestation_1", indexedAttestationSchema),
        namedSchema("attestation_2", indexedAttestationSchema));
  }

  @Override
  public AttesterSlashingPhase0 createFromBackingNode(final TreeNode node) {
    return new AttesterSlashingPhase0(this, node);
  }

  @Override
  public AttesterSlashing create(
      final IndexedAttestation attestation1, final IndexedAttestation attestation2) {
    return new AttesterSlashingPhase0(this, attestation1, attestation2);
  }
}
