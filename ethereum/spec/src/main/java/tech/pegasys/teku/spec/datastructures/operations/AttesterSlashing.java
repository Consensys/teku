/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.operations;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation.IndexedAttestationSchema;

public class AttesterSlashing
    extends Container2<AttesterSlashing, IndexedAttestation, IndexedAttestation> {

  public static class AttesterSlashingSchema
      extends ContainerSchema2<AttesterSlashing, IndexedAttestation, IndexedAttestation> {

    public AttesterSlashingSchema(final IndexedAttestationSchema indexedAttestationSchema) {
      super(
          "AttesterSlashing",
          namedSchema("attestation_1", indexedAttestationSchema),
          namedSchema("attestation_2", indexedAttestationSchema));
    }

    @Override
    public AttesterSlashing createFromBackingNode(TreeNode node) {
      return new AttesterSlashing(this, node);
    }

    public AttesterSlashing create(
        final IndexedAttestation attestation1, final IndexedAttestation attestation2) {
      return new AttesterSlashing(this, attestation1, attestation2);
    }
  }

  private final Supplier<Set<UInt64>> intersectingIndices =
      Suppliers.memoize(
          () ->
              Sets.intersection(
                  new TreeSet<>(
                      getAttestation1()
                          .getAttestingIndices()
                          .asListUnboxed()), // TreeSet as must be sorted
                  new HashSet<>(getAttestation2().getAttestingIndices().asListUnboxed())));

  private AttesterSlashing(AttesterSlashingSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  private AttesterSlashing(
      final AttesterSlashingSchema schema,
      final IndexedAttestation attestation1,
      final IndexedAttestation attestation2) {
    super(schema, attestation1, attestation2);
  }

  @Override
  public AttesterSlashingSchema getSchema() {
    return (AttesterSlashingSchema) super.getSchema();
  }

  public Set<UInt64> getIntersectingValidatorIndices() {
    return intersectingIndices.get();
  }

  public IndexedAttestation getAttestation1() {
    return getField0();
  }

  public IndexedAttestation getAttestation2() {
    return getField1();
  }
}
