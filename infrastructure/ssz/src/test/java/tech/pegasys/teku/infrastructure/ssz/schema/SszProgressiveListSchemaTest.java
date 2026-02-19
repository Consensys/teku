/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszProgressiveListSchemaTest {

  private static final SszProgressiveListSchema<SszUInt64> UINT64_LIST_SCHEMA =
      SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);

  @Test
  void primitiveListRoundtrip() {
    final List<SszUInt64> elements =
        IntStream.range(0, 10)
            .mapToObj(i -> SszUInt64.of(UInt64.valueOf(i)))
            .collect(Collectors.toList());

    final TreeNode tree = UINT64_LIST_SCHEMA.createTreeFromElements(elements);
    final SszList<SszUInt64> list = UINT64_LIST_SCHEMA.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(10);
    for (int i = 0; i < 10; i++) {
      assertThat(list.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }

    final Bytes ssz = list.sszSerialize();
    final SszList<SszUInt64> deserialized = UINT64_LIST_SCHEMA.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(10);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());

    for (int i = 0; i < 10; i++) {
      assertThat(deserialized.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }
  }

  @Test
  void fixedSizeCompositeListRoundtrip() {
    // Fixed-size composite elements (all fields fixed) — exercises sszSerializeFixed composite path
    final SszProgressiveContainerSchema<? extends SszContainer> elementSchema =
        new SszProgressiveContainerSchema<>(
            "FixedElement",
            new boolean[] {true, true},
            NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA));

    final SszProgressiveListSchema<? extends SszData> listSchema =
        SszProgressiveListSchema.create(elementSchema);

    final List<SszData> elements = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final TreeNode fieldTree =
          elementSchema.createTreeFromFieldValues(
              List.of(
                  SszUInt64.of(UInt64.valueOf(i)),
                  SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) i)));
      elements.add(elementSchema.createFromBackingNode(fieldTree));
    }

    @SuppressWarnings("unchecked")
    final SszProgressiveListSchema<SszData> rawSchema =
        (SszProgressiveListSchema<SszData>) listSchema;
    final TreeNode tree = rawSchema.createTreeFromElements(elements);
    final SszList<SszData> list = rawSchema.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(3);

    final Bytes ssz = list.sszSerialize();
    final SszList<SszData> deserialized = rawSchema.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(3);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void variableSizeCompositeListRoundtrip() {
    // Variable-size composite elements — exercises sszSerializeVariable path
    final SszProgressiveContainerSchema<? extends SszContainer> elementSchema =
        new SszProgressiveContainerSchema<>(
            "VarElement",
            new boolean[] {true, true},
            NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("b", new SszProgressiveBitlistSchema()));

    final SszProgressiveListSchema<? extends SszData> listSchema =
        SszProgressiveListSchema.create(elementSchema);

    final List<SszData> elements = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      final TreeNode fieldTree =
          elementSchema.createTreeFromFieldValues(
              List.of(
                  SszUInt64.of(UInt64.valueOf(i)),
                  new SszProgressiveBitlistSchema().ofBits(i + 1)));
      elements.add(elementSchema.createFromBackingNode(fieldTree));
    }

    @SuppressWarnings("unchecked")
    final SszProgressiveListSchema<SszData> rawSchema =
        (SszProgressiveListSchema<SszData>) listSchema;
    final TreeNode tree = rawSchema.createTreeFromElements(elements);
    final SszList<SszData> list = rawSchema.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(3);

    final Bytes ssz = list.sszSerialize();
    final SszList<SszData> deserialized = rawSchema.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(3);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void emptyList() {
    final SszList<SszUInt64> list = UINT64_LIST_SCHEMA.getDefault();
    assertThat(list.size()).isZero();
    assertThat(list.sszSerialize()).isEqualTo(Bytes.EMPTY);
    assertThat(list.hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
  }

  @Test
  void getMaxLength_shouldReturnMaxValue() {
    assertThat(UINT64_LIST_SCHEMA.getMaxLength()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void getElementSchema_shouldReturnElementSchema() {
    assertThat(UINT64_LIST_SCHEMA.getElementSchema()).isEqualTo(SszPrimitiveSchemas.UINT64_SCHEMA);
  }

  @Test
  void treeDepth_shouldThrow() {
    assertThatThrownBy(UINT64_LIST_SCHEMA::treeDepth)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void treeWidth_shouldThrow() {
    assertThatThrownBy(UINT64_LIST_SCHEMA::treeWidth)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void isFixedSize_shouldReturnFalse() {
    assertThat(UINT64_LIST_SCHEMA.isFixedSize()).isFalse();
  }

  @Test
  void getDefaultTree_shouldBeEmptyList() {
    final SszList<SszUInt64> defaultList =
        UINT64_LIST_SCHEMA.createFromBackingNode(UINT64_LIST_SCHEMA.getDefaultTree());
    assertThat(defaultList.size()).isZero();
  }

  @Test
  void getChildGeneralizedIndex_shouldReturnCorrectGIndex() {
    long gIdx0 = UINT64_LIST_SCHEMA.getChildGeneralizedIndex(0);
    long gIdx1 = UINT64_LIST_SCHEMA.getChildGeneralizedIndex(1);
    // Indices should be different for different elements
    assertThat(gIdx0).isNotEqualTo(gIdx1);
    // Both should be valid (positive)
    assertThat(gIdx0).isPositive();
    assertThat(gIdx1).isPositive();
  }

  @Test
  void equals_sameElementSchema_shouldBeEqual() {
    final SszProgressiveListSchema<SszUInt64> other =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);
    assertThat(UINT64_LIST_SCHEMA).isEqualTo(other);
    assertThat(UINT64_LIST_SCHEMA.hashCode()).isEqualTo(other.hashCode());
  }

  @Test
  void equals_differentElementSchema_shouldNotBeEqual() {
    final SszProgressiveListSchema<?> byteListSchema =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA);
    assertThat(UINT64_LIST_SCHEMA).isNotEqualTo(byteListSchema);
  }

  @Test
  void getName_shouldContainElementSchema() {
    assertThat(UINT64_LIST_SCHEMA.getName()).isPresent();
    assertThat(UINT64_LIST_SCHEMA.getName().get()).startsWith("ProgressiveList[");
  }

  @SuppressWarnings("unchecked")
  private static <T extends SszData> SszProgressiveListSchema<T> createProgressiveListSchema(
      final SszSchema<? extends T> elementSchema) {
    return new SszProgressiveListSchema<>((SszSchema<T>) elementSchema);
  }

  @Test
  void variableSizeElements_roundtrip() {
    // List of variable-size elements (lists inside a progressive list)
    final SszListSchema<SszUInt64, ?> innerListSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    final SszProgressiveListSchema<SszList<SszUInt64>> varListSchema =
        createProgressiveListSchema(innerListSchema);

    final SszList<SszUInt64> inner1 = innerListSchema.getDefault();
    final SszList<SszUInt64> inner2 =
        innerListSchema.createFromElements(List.of(SszUInt64.of(UInt64.valueOf(42))));

    final List<SszList<SszUInt64>> elements = List.of(inner1, inner2);
    final TreeNode tree = varListSchema.createTreeFromElements(elements);
    final SszList<SszList<SszUInt64>> list = varListSchema.createFromBackingNode(tree);

    assertThat(list.size()).isEqualTo(2);

    final Bytes ssz = list.sszSerialize();
    final SszList<SszList<SszUInt64>> deserialized = varListSchema.sszDeserialize(ssz);
    assertThat(deserialized.size()).isEqualTo(2);
    assertThat(deserialized.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void invalidSsz_firstOffsetExceedsData_shouldThrow() {
    // For variable-size elements, the first offset in SSZ cannot exceed available data
    final SszListSchema<SszUInt64, ?> innerListSchema =
        SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    final SszProgressiveListSchema<SszList<SszUInt64>> varListSchema =
        createProgressiveListSchema(innerListSchema);

    // Build invalid SSZ: 4-byte offset that points beyond available data
    final Bytes invalidSsz = Bytes.of(0xFF, 0x00, 0x00, 0x00);
    assertThatThrownBy(() -> varListSchema.sszDeserialize(invalidSsz))
        .isInstanceOf(SszDeserializeException.class);
  }
}
