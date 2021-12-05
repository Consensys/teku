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

public class AttesterSlashing
    extends Container2<AttesterSlashing, IndexedAttestation, IndexedAttestation> {

  public static class AttesterSlashingSchema
      extends ContainerSchema2<AttesterSlashing, IndexedAttestation, IndexedAttestation> {

    public AttesterSlashingSchema() {
      super(
          "AttesterSlashing",
          namedSchema("attestation_1", IndexedAttestation.SSZ_SCHEMA),
          namedSchema("attestation_2", IndexedAttestation.SSZ_SCHEMA));
    }

    @Override
    public AttesterSlashing createFromBackingNode(TreeNode node) {
      return new AttesterSlashing(this, node);
    }
  }

  public static final AttesterSlashingSchema SSZ_SCHEMA = new AttesterSlashingSchema();

  private final Supplier<Set<UInt64>> intersectingIndices =
      Suppliers.memoize(
          () ->
              Sets.intersection(
                  new TreeSet<>(
                      getAttestation_1()
                          .getAttesting_indices()
                          .asListUnboxed()), // TreeSet as must be sorted
                  new HashSet<>(getAttestation_2().getAttesting_indices().asListUnboxed())));

  private AttesterSlashing(AttesterSlashingSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttesterSlashing(IndexedAttestation attestation_1, IndexedAttestation attestation_2) {
    super(SSZ_SCHEMA, attestation_1, attestation_2);
  }

  public Set<UInt64> getIntersectingValidatorIndices() {
    return intersectingIndices.get();
  }

  public IndexedAttestation getAttestation_1() {
    return getField0();
  }

  public IndexedAttestation getAttestation_2() {
    return getField1();
  }
}
