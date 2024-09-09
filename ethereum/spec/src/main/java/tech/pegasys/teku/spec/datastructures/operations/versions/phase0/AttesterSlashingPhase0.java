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

import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IntersectingIndicesCalculator;

public class AttesterSlashingPhase0
    extends Container2<AttesterSlashingPhase0, IndexedAttestation, IndexedAttestation>
    implements AttesterSlashing {
  private final IntersectingIndicesCalculator intersectingIndicesCalculator;

  AttesterSlashingPhase0(final AttesterSlashingPhase0Schema type, final TreeNode backingNode) {
    super(type, backingNode);
    this.intersectingIndicesCalculator = new IntersectingIndicesCalculator(this);
  }

  AttesterSlashingPhase0(
      final AttesterSlashingPhase0Schema schema,
      final IndexedAttestation attestation1,
      final IndexedAttestation attestation2) {
    super(schema, attestation1, attestation2);
    this.intersectingIndicesCalculator = new IntersectingIndicesCalculator(this);
  }

  @Override
  public AttesterSlashingPhase0Schema getSchema() {
    return (AttesterSlashingPhase0Schema) super.getSchema();
  }

  @Override
  public Set<UInt64> getIntersectingValidatorIndices() {
    return intersectingIndicesCalculator.getIntersectingValidatorIndices();
  }

  @Override
  public IndexedAttestation getAttestation1() {
    return getField0();
  }

  @Override
  public IndexedAttestation getAttestation2() {
    return getField1();
  }
}
