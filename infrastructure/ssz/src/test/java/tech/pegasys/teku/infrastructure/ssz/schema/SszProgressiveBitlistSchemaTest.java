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
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import java.util.BitSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszProgressiveBitlistSchemaTest {

  private static final SszProgressiveBitlistSchema SCHEMA = new SszProgressiveBitlistSchema();

  @Test
  void ofBits_test() {
    assertThat(SCHEMA.ofBits(1).sszSerialize()).isEqualTo(Bytes.of(0b10));
    assertThat(SCHEMA.ofBits(1, 0).sszSerialize()).isEqualTo(Bytes.of(0b11));
    assertThat(SCHEMA.ofBits(2).sszSerialize()).isEqualTo(Bytes.of(0b100));
    assertThat(SCHEMA.ofBits(2, 0).sszSerialize()).isEqualTo(Bytes.of(0b101));
    assertThat(SCHEMA.ofBits(2, 1).sszSerialize()).isEqualTo(Bytes.of(0b110));
    assertThat(SCHEMA.ofBits(2, 0, 1).sszSerialize()).isEqualTo(Bytes.of(0b111));
  }

  @Test
  void ofBits_withSetBits() {
    final SszBitlist bitlist = SCHEMA.ofBits(300, 0, 100, 299);
    assertThat(bitlist.size()).isEqualTo(300);
    assertThat(bitlist.getBit(0)).isTrue();
    assertThat(bitlist.getBit(100)).isTrue();
    assertThat(bitlist.getBit(299)).isTrue();
    assertThat(bitlist.getBit(1)).isFalse();
    assertThat(bitlist.getBitCount()).isEqualTo(3);
    assertThatIntCollection(bitlist.getAllSetBits()).containsExactly(0, 100, 299);
  }

  @Test
  void createFromElements_shouldReturnSszBitlist() {
    final SszBitlist bitlist =
        SCHEMA.createFromElements(List.of(SszBit.of(false), SszBit.of(true)));
    assertThat(bitlist).isInstanceOf(SszBitlist.class);
    assertThat(bitlist.size()).isEqualTo(2);
    assertThat(bitlist.getBit(0)).isFalse();
    assertThat(bitlist.getBit(1)).isTrue();
  }

  static Stream<Arguments> createTreeFromBitDataCases() {
    return Stream.of(
        Arguments.of(0, new int[] {}),
        Arguments.of(1, new int[] {}),
        Arguments.of(1, new int[] {0}),
        Arguments.of(7, new int[] {0, 3, 6}),
        Arguments.of(8, new int[] {0, 7}),
        Arguments.of(9, new int[] {0, 8}),
        Arguments.of(16, new int[] {}),
        Arguments.of(255, new int[] {0, 127, 254}),
        Arguments.of(256, new int[] {0, 128, 255}),
        Arguments.of(257, new int[] {0, 256}),
        Arguments.of(500, IntStream.range(0, 500).filter(i -> i % 3 == 0).toArray()));
  }

  @ParameterizedTest
  @MethodSource("createTreeFromBitDataCases")
  void createTreeFromBitData_shouldMatchSszDeserializedTree(final int size, final int[] setBits) {
    final BitSet bitSet = new BitSet(size);
    for (int bit : setBits) {
      bitSet.set(bit);
    }
    final TreeNode directTree = SCHEMA.createTreeFromBitData(size, bitSet.toByteArray());

    final SszBitlist bitlist = SCHEMA.ofBits(size, setBits);
    final Bytes ssz = bitlist.sszSerialize();
    final TreeNode deserializedTree;
    try (SszReader reader = SszReader.fromBytes(ssz)) {
      deserializedTree = SCHEMA.sszDeserializeTree(reader);
    }

    assertThat(directTree.hashTreeRoot()).isEqualTo(deserializedTree.hashTreeRoot());
  }

  @Test
  void getMaxLength_shouldReturnMaxValue() {
    assertThat(SCHEMA.getMaxLength()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void getElementSchema_shouldReturnBitSchema() {
    assertThat(SCHEMA.getElementSchema()).isEqualTo(SszPrimitiveSchemas.BIT_SCHEMA);
  }

  @Test
  void getElementsPerChunk_shouldReturn256() {
    assertThat(SCHEMA.getElementsPerChunk()).isEqualTo(256);
  }

  @Test
  void treeDepth_shouldThrow() {
    assertThatThrownBy(SCHEMA::treeDepth).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void treeWidth_shouldThrow() {
    assertThatThrownBy(SCHEMA::treeWidth).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void isFixedSize_shouldReturnFalse() {
    assertThat(SCHEMA.isFixedSize()).isFalse();
  }

  @Test
  void getDefaultTree_shouldBeEmptySize0() {
    final SszBitlist defaultBitlist = SCHEMA.createFromBackingNode(SCHEMA.getDefaultTree());
    assertThat(defaultBitlist.size()).isZero();
  }

  @Test
  void sszDeserialize_emptyBytes_shouldThrow() {
    assertThatThrownBy(() -> SCHEMA.sszDeserialize(Bytes.EMPTY)).isInstanceOf(Exception.class);
  }

  @Test
  void sszDeserialize_zeroOnlyByte_shouldThrow() {
    assertThatThrownBy(() -> SCHEMA.sszDeserialize(Bytes.of(0))).isInstanceOf(Exception.class);
  }

  @Test
  void getName_shouldReturnProgressiveBitlist() {
    assertThat(SCHEMA.getName()).hasValue("ProgressiveBitlist");
  }

  @Test
  void equals_allInstancesShouldBeEqual() {
    final SszProgressiveBitlistSchema other = new SszProgressiveBitlistSchema();
    assertThat(SCHEMA).isEqualTo(other);
    assertThat(SCHEMA.hashCode()).isEqualTo(other.hashCode());
  }

  // ===== SszLengthBounds overflow tests =====

  @Test
  void getSszLengthBounds_maxBytesShouldBePositive() {
    final SszLengthBounds bounds = SCHEMA.getSszLengthBounds();
    assertThat(bounds.getMaxBytes()).isPositive();
  }

  @Test
  void getSszLengthBounds_isWithinBounds_shouldAcceptReasonableSizes() {
    final SszLengthBounds bounds = SCHEMA.getSszLengthBounds();
    assertThat(bounds.isWithinBounds(1000)).isTrue();
  }

  @Test
  void progressiveBitlistAsFieldOfStandardContainer_boundsShouldNotOverflow() {
    final ContainerSchema2<SszContainer, SszUInt64, SszBitlist> containerSchema =
        ContainerSchema2.create(
            SszPrimitiveSchemas.UINT64_SCHEMA,
            SCHEMA,
            (schema, node) -> {
              throw new UnsupportedOperationException("not needed for bounds test");
            });

    final SszLengthBounds bounds = containerSchema.getSszLengthBounds();
    assertThat(bounds.getMaxBytes()).isPositive();
    assertThat(bounds.isWithinBounds(1000)).isTrue();
  }
}
