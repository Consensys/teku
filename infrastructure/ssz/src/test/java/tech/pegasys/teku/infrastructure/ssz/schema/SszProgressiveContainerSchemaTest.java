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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszProgressiveContainerSchemaTest {

  private static final SszProgressiveContainerSchema<SszContainer> SINGLE_FIELD_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "SingleField",
          new boolean[] {true},
          NamedSchema.of("field0", SszPrimitiveSchemas.UINT64_SCHEMA));

  private static final SszProgressiveContainerSchema<SszContainer> MULTI_FIELD_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "MultiField",
          new boolean[] {true, true, true},
          NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA),
          NamedSchema.of("c", SszPrimitiveSchemas.UINT64_SCHEMA));

  private static final SszProgressiveContainerSchema<SszContainer> GAPPED_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "GappedContainer",
          new boolean[] {true, false, true},
          NamedSchema.of("first", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("third", SszPrimitiveSchemas.BYTE_SCHEMA));

  @Test
  void singleFieldContainer_sszRoundtrip() {
    final TreeNode tree =
        SINGLE_FIELD_SCHEMA.createTreeFromFieldValues(List.of(SszUInt64.of(UInt64.valueOf(42))));
    final SszContainer container = SINGLE_FIELD_SCHEMA.createFromBackingNode(tree);

    final Bytes ssz = container.sszSerialize();
    final SszContainer deserialized = SINGLE_FIELD_SCHEMA.sszDeserialize(ssz);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(container.hashTreeRoot());
    assertThat(((SszUInt64) deserialized.get(0)).get()).isEqualTo(UInt64.valueOf(42));
  }

  @Test
  void multiFieldContainer_sszRoundtrip() {
    final TreeNode tree =
        MULTI_FIELD_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(100)),
                SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) 7),
                SszUInt64.of(UInt64.valueOf(200))));
    final SszContainer container = MULTI_FIELD_SCHEMA.createFromBackingNode(tree);

    final Bytes ssz = container.sszSerialize();
    final SszContainer deserialized = MULTI_FIELD_SCHEMA.sszDeserialize(ssz);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(container.hashTreeRoot());
    assertThat(deserialized.size()).isEqualTo(3);
    assertThat(((SszUInt64) deserialized.get(0)).get()).isEqualTo(UInt64.valueOf(100));
    assertThat(((SszByte) deserialized.get(1)).get()).isEqualTo((byte) 7);
    assertThat(((SszUInt64) deserialized.get(2)).get()).isEqualTo(UInt64.valueOf(200));
  }

  @Test
  void gappedContainer_sszRoundtrip() {
    final TreeNode tree =
        GAPPED_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(99)), SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) 3)));
    final SszContainer container = GAPPED_SCHEMA.createFromBackingNode(tree);

    final Bytes ssz = container.sszSerialize();
    final SszContainer deserialized = GAPPED_SCHEMA.sszDeserialize(ssz);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(container.hashTreeRoot());
    assertThat(deserialized.size()).isEqualTo(2);
    assertThat(((SszUInt64) deserialized.get(0)).get()).isEqualTo(UInt64.valueOf(99));
    assertThat(((SszByte) deserialized.get(1)).get()).isEqualTo((byte) 3);
  }

  @Test
  void variableSizeFields_sszRoundtrip() {
    final SszProgressiveContainerSchema<SszContainer> varSchema =
        new SszProgressiveContainerSchema<>(
            "VarContainer",
            new boolean[] {true, true},
            NamedSchema.of("fixed", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("varField", SszBitlistSchema.create(100)));

    final TreeNode tree =
        varSchema.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(5)), SszBitlistSchema.create(100).ofBits(10, 0, 3, 9)));
    final SszContainer container = varSchema.createFromBackingNode(tree);

    final Bytes ssz = container.sszSerialize();
    final SszContainer deserialized = varSchema.sszDeserialize(ssz);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(container.hashTreeRoot());
    assertThat(deserialized.size()).isEqualTo(2);
  }

  @Test
  void getActiveFields_shouldReturnClonedCopy() {
    final boolean[] activeFields = GAPPED_SCHEMA.getActiveFields();
    assertThat(activeFields).isEqualTo(new boolean[] {true, false, true});

    // Modifying returned array should not affect schema
    activeFields[0] = false;
    assertThat(GAPPED_SCHEMA.getActiveFields()[0]).isTrue();
  }

  @Test
  void getTreePosition_shouldMapFieldIndexToSlotPosition() {
    // GAPPED_SCHEMA: activeFields = [true, false, true]
    // field 0 → slot 0, field 1 → slot 2
    assertThat(GAPPED_SCHEMA.getTreePosition(0)).isZero();
    assertThat(GAPPED_SCHEMA.getTreePosition(1)).isEqualTo(2);
  }

  @Test
  void getFieldIndex_byNameLookup() {
    assertThat(MULTI_FIELD_SCHEMA.getFieldIndex("a")).isZero();
    assertThat(MULTI_FIELD_SCHEMA.getFieldIndex("b")).isEqualTo(1);
    assertThat(MULTI_FIELD_SCHEMA.getFieldIndex("c")).isEqualTo(2);
    assertThat(MULTI_FIELD_SCHEMA.getFieldIndex("nonexistent")).isEqualTo(-1);
  }

  @Test
  void getFieldNames_shouldReturnActiveFieldNames() {
    assertThat(GAPPED_SCHEMA.getFieldNames()).containsExactly("first", "third");
    assertThat(MULTI_FIELD_SCHEMA.getFieldNames()).containsExactly("a", "b", "c");
  }

  @Test
  void getFieldSchemas_shouldReturnActiveFieldSchemas() {
    final List<? extends SszSchema<?>> schemas = MULTI_FIELD_SCHEMA.getFieldSchemas();
    assertThat(schemas).hasSize(3);
    assertThat(schemas.get(0)).isEqualTo(SszPrimitiveSchemas.UINT64_SCHEMA);
    assertThat(schemas.get(1)).isEqualTo(SszPrimitiveSchemas.BYTE_SCHEMA);
    assertThat(schemas.get(2)).isEqualTo(SszPrimitiveSchemas.UINT64_SCHEMA);
  }

  @Test
  void isFixedSize_allFixed_shouldReturnTrue() {
    assertThat(MULTI_FIELD_SCHEMA.isFixedSize()).isTrue();
  }

  @Test
  void isFixedSize_withVariable_shouldReturnFalse() {
    final SszProgressiveContainerSchema<SszContainer> varSchema =
        new SszProgressiveContainerSchema<>(
            "VarTest",
            new boolean[] {true, true},
            NamedSchema.of("fixed", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("var", SszBitlistSchema.create(100)));
    assertThat(varSchema.isFixedSize()).isFalse();
  }

  @Test
  void treeDepth_shouldThrow() {
    assertThatThrownBy(MULTI_FIELD_SCHEMA::treeDepth)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void treeWidth_shouldThrow() {
    assertThatThrownBy(MULTI_FIELD_SCHEMA::treeWidth)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void createTreeFromFieldValues_wrongCount_shouldThrow() {
    assertThatThrownBy(
            () -> MULTI_FIELD_SCHEMA.createTreeFromFieldValues(List.of(SszUInt64.of(UInt64.ZERO))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equals_sameSchemas_shouldBeEqual() {
    final SszProgressiveContainerSchema<SszContainer> other =
        new SszProgressiveContainerSchema<>(
            "MultiField",
            new boolean[] {true, true, true},
            NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA),
            NamedSchema.of("c", SszPrimitiveSchemas.UINT64_SCHEMA));
    assertThat(MULTI_FIELD_SCHEMA).isEqualTo(other);
    assertThat(MULTI_FIELD_SCHEMA.hashCode()).isEqualTo(other.hashCode());
  }

  @Test
  void equals_differentActiveFields_shouldNotBeEqual() {
    final SszProgressiveContainerSchema<SszContainer> other =
        new SszProgressiveContainerSchema<>(
            "MultiField",
            new boolean[] {true, false, true, true},
            NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
            NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA),
            NamedSchema.of("c", SszPrimitiveSchemas.UINT64_SCHEMA));
    assertThat(MULTI_FIELD_SCHEMA).isNotEqualTo(other);
  }

  @Test
  void getName_shouldReturnContainerName() {
    assertThat(MULTI_FIELD_SCHEMA.getName()).hasValue("MultiField");
    assertThat(GAPPED_SCHEMA.getName()).hasValue("GappedContainer");
  }

  @Test
  void defaultTree_shouldCreateValidDefaultContainer() {
    final SszContainer defaultContainer =
        MULTI_FIELD_SCHEMA.createFromBackingNode(MULTI_FIELD_SCHEMA.getDefaultTree());
    assertThat(defaultContainer.size()).isEqualTo(3);

    // All fields should have default values
    final SszData field0 = defaultContainer.get(0);
    assertThat(field0).isNotNull();
    final SszData field1 = defaultContainer.get(1);
    assertThat(field1).isNotNull();
    final SszData field2 = defaultContainer.get(2);
    assertThat(field2).isNotNull();
  }

  @Test
  void constructor_emptyActiveFields_shouldThrow() {
    assertThatThrownBy(() -> new SszProgressiveContainerSchema<>("Empty", new boolean[] {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_lastElementFalse_shouldThrow() {
    assertThatThrownBy(
            () ->
                new SszProgressiveContainerSchema<>(
                    "Bad",
                    new boolean[] {true, false},
                    NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_mismatchedSchemaCount_shouldThrow() {
    assertThatThrownBy(
            () ->
                new SszProgressiveContainerSchema<>(
                    "Mismatch",
                    new boolean[] {true, true},
                    NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getChildGeneralizedIndex_shouldReturnDifferentForDifferentFields() {
    final long gIdx0 = MULTI_FIELD_SCHEMA.getChildGeneralizedIndex(0);
    final long gIdx1 = MULTI_FIELD_SCHEMA.getChildGeneralizedIndex(1);
    final long gIdx2 = MULTI_FIELD_SCHEMA.getChildGeneralizedIndex(2);

    assertThat(gIdx0).isPositive();
    assertThat(gIdx1).isPositive();
    assertThat(gIdx2).isPositive();
    assertThat(gIdx0).isNotEqualTo(gIdx1);
    assertThat(gIdx1).isNotEqualTo(gIdx2);
  }
}
