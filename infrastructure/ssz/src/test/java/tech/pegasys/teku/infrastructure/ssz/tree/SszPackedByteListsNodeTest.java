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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;

public class SszPackedByteListsNodeTest {

  static final SszByteListSchema<SszByteList> SMALL_ELEMENT = SszByteListSchema.create(256);
  static final SszListSchema<SszByteList, ?> SMALL_ORACLE = SszListSchema.create(SMALL_ELEMENT, 16);
  static final SszByteListSchema<SszByteList> HUGE_ELEMENT = SszByteListSchema.create(1L << 30);
  static final SszListSchema<SszByteList, ?> HUGE_ORACLE =
      SszListSchema.create(HUGE_ELEMENT, 1L << 20);

  /**
   * Builds the SSZ variable part (offset table + data) for the given element payloads. Public: also
   * used by SszPackedByteListsSchemaTest in the schema package (Tasks 4-7).
   */
  public static Bytes serializeElements(final List<Bytes> elements) {
    final int count = elements.size();
    final byte[] offsetBytes = new byte[count * 4];
    int offset = count * 4;
    for (int i = 0; i < count; i++) {
      offsetBytes[i * 4] = (byte) offset;
      offsetBytes[i * 4 + 1] = (byte) (offset >> 8);
      offsetBytes[i * 4 + 2] = (byte) (offset >> 16);
      offsetBytes[i * 4 + 3] = (byte) (offset >> 24);
      offset += elements.get(i).size();
    }
    final Bytes[] parts = new Bytes[count + 1];
    parts[0] = Bytes.wrap(offsetBytes);
    for (int i = 0; i < count; i++) {
      parts[i + 1] = elements.get(i);
    }
    return Bytes.wrap(parts);
  }

  static int[] offsetsOf(final List<Bytes> elements) {
    final int count = elements.size();
    final int[] offsets = new int[count + 1];
    int offset = count * 4;
    for (int i = 0; i < count; i++) {
      offsets[i] = offset;
      offset += elements.get(i).size();
    }
    offsets[count] = offset;
    return offsets;
  }

  static SszPackedByteListsNode node(
      final List<Bytes> elements,
      final SszByteListSchema<SszByteList> elementSchema,
      final SszListSchema<SszByteList, ?> oracleSchema) {
    final Function<Bytes, TreeNode> materializer =
        elementSsz -> {
          try (SszReader reader = SszReader.fromBytes(elementSsz)) {
            return elementSchema.sszDeserializeTree(reader);
          }
        };
    return new SszPackedByteListsNode(
        serializeElements(elements),
        offsetsOf(elements),
        elementSchema.treeDepth(),
        oracleSchema.treeDepth(),
        materializer);
  }

  public static List<Bytes> elementsOfSizes(final int... sizes) {
    return IntStream.range(0, sizes.length)
        .mapToObj(
            i -> {
              final byte[] data = new byte[sizes[i]];
              for (int j = 0; j < data.length; j++) {
                data[j] = (byte) (i + j + 1);
              }
              return Bytes.wrap(data);
            })
        .toList();
  }

  static TreeNode oracleVectorNode(
      final SszListSchema<SszByteList, ?> oracleSchema, final List<Bytes> elements) {
    // left child of the unhinted list root == the materialized vector node
    return oracleSchema
        .sszDeserialize(serializeElements(elements))
        .getBackingNode()
        .get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  static List<int[]> sizeMatrix() {
    return List.of(
        new int[] {0},
        new int[] {1},
        new int[] {31},
        new int[] {32},
        new int[] {33},
        new int[] {100},
        new int[] {0, 1, 31, 32, 33, 100},
        new int[] {255, 256});
  }

  @ParameterizedTest
  @MethodSource("sizeMatrix")
  public void hashTreeRoot_shouldMatchOracle_smallSchema(final int[] sizes) {
    final List<Bytes> elements = elementsOfSizes(sizes);
    assertThat(node(elements, SMALL_ELEMENT, SMALL_ORACLE).hashTreeRoot())
        .isEqualTo(oracleVectorNode(SMALL_ORACLE, elements).hashTreeRoot());
  }

  @ParameterizedTest
  @MethodSource("sizeMatrix")
  public void hashTreeRoot_shouldMatchOracle_hugeSchema(final int[] sizes) {
    final List<Bytes> elements = elementsOfSizes(sizes);
    assertThat(node(elements, HUGE_ELEMENT, HUGE_ORACLE).hashTreeRoot())
        .isEqualTo(oracleVectorNode(HUGE_ORACLE, elements).hashTreeRoot());
  }

  @ParameterizedTest
  @MethodSource("boundaryCounts")
  public void hashTreeRoot_shouldMatchOracleAtFullBoundary(final int count) {
    final List<Bytes> elements = elementsOfSizes(IntStream.range(0, count).map(i -> 3).toArray());
    assertThat(node(elements, SMALL_ELEMENT, SMALL_ORACLE).hashTreeRoot())
        .isEqualTo(oracleVectorNode(SMALL_ORACLE, elements).hashTreeRoot());
  }

  static List<Integer> boundaryCounts() {
    return List.of(1, 2, 15, 16);
  }

  @ParameterizedTest
  @MethodSource("sparseCounts")
  public void hashTreeRoot_shouldMatchOracleForSparseHugeList(final int count) {
    final List<Bytes> elements = elementsOfSizes(IntStream.range(0, count).map(i -> 5).toArray());
    assertThat(node(elements, HUGE_ELEMENT, HUGE_ORACLE).hashTreeRoot())
        .isEqualTo(oracleVectorNode(HUGE_ORACLE, elements).hashTreeRoot());
  }

  static List<Integer> sparseCounts() {
    return List.of(1, 2, 33, 100);
  }
}
