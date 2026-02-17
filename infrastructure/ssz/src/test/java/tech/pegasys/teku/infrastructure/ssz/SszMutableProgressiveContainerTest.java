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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SszMutableProgressiveContainerTest {

  private static final SszProgressiveContainerSchema<SszContainer> ALL_FIXED_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "AllFixed",
          new boolean[] {true, true, true},
          NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("b", SszPrimitiveSchemas.BYTE_SCHEMA),
          NamedSchema.of("c", SszPrimitiveSchemas.UINT64_SCHEMA));

  private static final SszProgressiveContainerSchema<SszContainer> GAPPED_SCHEMA =
      new SszProgressiveContainerSchema<>(
          "Gapped",
          new boolean[] {true, false, true},
          NamedSchema.of("first", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("third", SszPrimitiveSchemas.BYTE_SCHEMA));

  private static SszContainer createAllFixed(final UInt64 a, final byte b, final UInt64 c) {
    TreeNode tree =
        ALL_FIXED_SCHEMA.createTreeFromFieldValues(
            List.of(SszUInt64.of(a), SszPrimitiveSchemas.BYTE_SCHEMA.boxed(b), SszUInt64.of(c)));
    return ALL_FIXED_SCHEMA.createFromBackingNode(tree);
  }

  private static SszContainer createGapped(final UInt64 first, final byte third) {
    TreeNode tree =
        GAPPED_SCHEMA.createTreeFromFieldValues(
            List.of(SszUInt64.of(first), SszPrimitiveSchemas.BYTE_SCHEMA.boxed(third)));
    return GAPPED_SCHEMA.createFromBackingNode(tree);
  }

  @Test
  void isWritableSupported_shouldReturnTrue() {
    SszContainer container = createAllFixed(UInt64.ONE, (byte) 0, UInt64.ZERO);
    assertThat(container.isWritableSupported()).isTrue();
  }

  @Test
  void createWritableCopy_shouldSucceed() {
    SszContainer container = createAllFixed(UInt64.valueOf(10), (byte) 20, UInt64.valueOf(30));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();
    assertThat(mutable.size()).isEqualTo(3);
    assertThat(((SszUInt64) mutable.get(0)).get()).isEqualTo(UInt64.valueOf(10));
  }

  @Test
  void setAndGet_roundtrip() {
    SszContainer container = createAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));

    assertThat(((SszUInt64) mutable.get(0)).get()).isEqualTo(UInt64.valueOf(100));
    assertThat(((SszByte) mutable.get(1)).get()).isEqualTo((byte) 2);
    assertThat(((SszUInt64) mutable.get(2)).get()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  void commitChanges_producesCorrectImmutable() {
    SszContainer container = createAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(1, SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) 99));
    SszContainer committed = mutable.commitChanges();

    assertThat(((SszUInt64) committed.get(0)).get()).isEqualTo(UInt64.ONE);
    assertThat(((SszByte) committed.get(1)).get()).isEqualTo((byte) 99);
    assertThat(((SszUInt64) committed.get(2)).get()).isEqualTo(UInt64.valueOf(3));
  }

  @Test
  void commitChanges_hashTreeRootMatchesFreshCreation() {
    SszContainer container = createAllFixed(UInt64.valueOf(1), (byte) 2, UInt64.valueOf(3));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));
    mutable.set(2, SszUInt64.of(UInt64.valueOf(300)));
    SszContainer committed = mutable.commitChanges();

    SszContainer expected = createAllFixed(UInt64.valueOf(100), (byte) 2, UInt64.valueOf(300));
    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void unchangedFields_remainUntouched() {
    SszContainer container = createAllFixed(UInt64.valueOf(10), (byte) 20, UInt64.valueOf(30));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    // Only modify field 1
    mutable.set(1, SszPrimitiveSchemas.BYTE_SCHEMA.boxed((byte) 99));
    SszContainer committed = mutable.commitChanges();

    assertThat(((SszUInt64) committed.get(0)).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(((SszByte) committed.get(1)).get()).isEqualTo((byte) 99);
    assertThat(((SszUInt64) committed.get(2)).get()).isEqualTo(UInt64.valueOf(30));
  }

  @Test
  void gappedContainer_setAndCommit() {
    SszContainer container = createGapped(UInt64.valueOf(42), (byte) 7);
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));
    SszContainer committed = mutable.commitChanges();

    assertThat(((SszUInt64) committed.get(0)).get()).isEqualTo(UInt64.valueOf(100));
    assertThat(((SszByte) committed.get(1)).get()).isEqualTo((byte) 7);

    // Verify hash matches fresh creation
    SszContainer expected = createGapped(UInt64.valueOf(100), (byte) 7);
    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void multipleCommits() {
    SszContainer container = createAllFixed(UInt64.ONE, (byte) 2, UInt64.valueOf(3));

    // First mutation
    SszMutableContainer mutable1 = (SszMutableContainer) container.createWritableCopy();
    mutable1.set(0, SszUInt64.of(UInt64.valueOf(10)));
    SszContainer committed1 = mutable1.commitChanges();

    // Second mutation
    SszMutableContainer mutable2 = (SszMutableContainer) committed1.createWritableCopy();
    mutable2.set(2, SszUInt64.of(UInt64.valueOf(30)));
    SszContainer committed2 = mutable2.commitChanges();

    assertThat(((SszUInt64) committed2.get(0)).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(((SszByte) committed2.get(1)).get()).isEqualTo((byte) 2);
    assertThat(((SszUInt64) committed2.get(2)).get()).isEqualTo(UInt64.valueOf(30));

    SszContainer expected = createAllFixed(UInt64.valueOf(10), (byte) 2, UInt64.valueOf(30));
    assertThat(committed2.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void commitWithNoChanges_returnsSameData() {
    SszContainer container = createAllFixed(UInt64.ONE, (byte) 2, UInt64.valueOf(3));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();

    SszContainer committed = mutable.commitChanges();
    assertThat(committed.hashTreeRoot()).isEqualTo(container.hashTreeRoot());
  }

  @Test
  void sszSerializationRoundtrip_afterMutation() {
    SszContainer container = createAllFixed(UInt64.ONE, (byte) 2, UInt64.valueOf(3));
    SszMutableContainer mutable = (SszMutableContainer) container.createWritableCopy();
    mutable.set(0, SszUInt64.of(UInt64.valueOf(42)));
    SszContainer committed = mutable.commitChanges();

    Bytes serialized = committed.sszSerialize();
    SszContainer deserialized = ALL_FIXED_SCHEMA.sszDeserialize(serialized);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(committed.hashTreeRoot());
    assertThat(((SszUInt64) deserialized.get(0)).get()).isEqualTo(UInt64.valueOf(42));
  }
}
