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

package tech.pegasys.teku.datastructures.operations;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class AttesterSlashing
    extends Container2<AttesterSlashing, IndexedAttestation, IndexedAttestation>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  @SszTypeDescriptor
  public static final ContainerType2<AttesterSlashing, IndexedAttestation, IndexedAttestation>
      TYPE =
          ContainerType2.create(
              IndexedAttestation.TYPE, IndexedAttestation.TYPE, AttesterSlashing::new);

  private IndexedAttestation attestation_1;
  private IndexedAttestation attestation_2;

  @Label("sos-ignore")
  private final Supplier<Set<UInt64>> intersectingIndices =
      Suppliers.memoize(
          () ->
              Sets.intersection(
                  new TreeSet<>(
                      getAttestation_1()
                          .getAttesting_indices()
                          .asList()), // TreeSet as must be sorted
                  new HashSet<>(getAttestation_2().getAttesting_indices().asList())));

  public AttesterSlashing(
      ContainerType2<AttesterSlashing, IndexedAttestation, IndexedAttestation> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttesterSlashing(IndexedAttestation attestation_1, IndexedAttestation attestation_2) {
    super(TYPE, attestation_1, attestation_2);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(
        SimpleOffsetSerializer.serialize(attestation_1),
        SimpleOffsetSerializer.serialize(attestation_2));
  }

  @Override
  public int hashCode() {
    return Objects.hash(attestation_1, attestation_2);
  }

  public Set<UInt64> getIntersectingValidatorIndices() {
    return intersectingIndices.get();
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AttesterSlashing)) {
      return false;
    }

    AttesterSlashing other = (AttesterSlashing) obj;
    return Objects.equals(this.getAttestation_1(), other.getAttestation_1())
        && Objects.equals(this.getAttestation_2(), other.getAttestation_2());
  }

  public IndexedAttestation getAttestation_1() {
    return getField0();
  }

  public IndexedAttestation getAttestation_2() {
    return getField1();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(attestation_1.hash_tree_root(), attestation_2.hash_tree_root()));
  }
}
