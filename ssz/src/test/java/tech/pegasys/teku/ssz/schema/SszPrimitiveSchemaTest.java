/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.ssz.SszDataAssert;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.primitive.SszBit;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeNodeVisitor;

public class SszPrimitiveSchemaTest implements SszSchemaTestBase {

  private final RandomSszDataGenerator randomSsz = new RandomSszDataGenerator();

  @Override
  public Stream<SszPrimitiveSchema<?, ?>> testSchemas() {
    return Stream.of(
        SszPrimitiveSchemas.BIT_SCHEMA,
        SszPrimitiveSchemas.BYTE_SCHEMA,
        SszPrimitiveSchemas.UINT64_SCHEMA,
        SszPrimitiveSchemas.BYTES4_SCHEMA,
        SszPrimitiveSchemas.BYTES32_SCHEMA);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void isPrimitive_shouldReturnTrue(SszPrimitiveSchema<?, ?> schema) {
    assertThat(schema.isPrimitive()).isTrue();
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void getDefaultTree_shouldReturnLeaf(SszPrimitiveSchema<?, ?> schema) {
    assertThat(schema.getDefaultTree()).isInstanceOf(LeafNode.class);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  <V, SszV extends SszPrimitive<V, SszV>> void boxed_roundtrip(SszPrimitiveSchema<V, SszV> schema) {
    SszV d = randomSsz.randomData(schema);
    V v = d.get();
    SszV d1 = schema.boxed(v);

    SszDataAssert.assertThatSszData(d1).isEqualByAllMeansTo(d);

    V v1 = d1.get();

    assertThat(v1).isEqualTo(v);
  }

  @Test
  void getBitsSize_shouldReturnCorrectValue() {
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.getBitsSize()).isEqualTo(1);
    assertThat(SszPrimitiveSchemas.BYTE_SCHEMA.getBitsSize()).isEqualTo(8);
    assertThat(SszPrimitiveSchemas.UINT64_SCHEMA.getBitsSize()).isEqualTo(64);
    assertThat(SszPrimitiveSchemas.BYTES4_SCHEMA.getBitsSize()).isEqualTo(32);
    assertThat(SszPrimitiveSchemas.BYTES32_SCHEMA.getBitsSize()).isEqualTo(256);
  }

  @Test
  void sszDeserializeTree_shouldRejectValuesPaddedWithNonZero() {
    assertThatThrownBy(
            () -> SszPrimitiveSchemas.BIT_SCHEMA.sszDeserialize(Bytes.fromHexString("0xda")))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void sszDeserializeTree_shouldAcceptValuesPaddedWithZero() {
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.sszDeserialize(Bytes.fromHexString("0x01")))
        .isSameAs(SszBit.of(true));
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.sszDeserialize(Bytes.fromHexString("0x00")))
        .isSameAs(SszBit.of(false));
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void iterate_shouldVisitBackingNodeAsLeaf(SszPrimitiveSchema<?, ?> schema) {
    final SszPrimitive<?, ?> primitive = randomSsz.randomData(schema);
    final TreeNode node = primitive.getBackingNode();
    final TreeNodeVisitor nodeVisitor = mock(TreeNodeVisitor.class);
    final int rootGIndex = 50;
    schema.iterate(nodeVisitor, 100, rootGIndex, node);
    verify(nodeVisitor).onLeafNode((LeafDataNode) node, rootGIndex);
    verifyNoMoreInteractions(nodeVisitor);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void loadBackingNodes_shouldRestoreTree(SszPrimitiveSchema<?, ?> schema) {
    final InMemoryStoringTreeNodeVisitor nodeStore = new InMemoryStoringTreeNodeVisitor();
    final SszPrimitive<?, ?> primitive = randomSsz.randomData(schema);
    final TreeNode node = primitive.getBackingNode();
    final int rootGIndex = 50;
    schema.iterate(nodeStore, 100, rootGIndex, node);
    final TreeNode result =
        schema.loadBackingNodes(nodeStore, primitive.hashTreeRoot(), rootGIndex);
    assertThat(result).isEqualTo(node);
    final SszPrimitive<?, ?> rebuiltPrimitive = schema.createFromBackingNode(result);
    assertThat(rebuiltPrimitive.get()).isEqualTo(primitive.get());
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void loadBackingNodes_shouldRestoreDefaultTree(SszPrimitiveSchema<?, ?> schema) {
    final InMemoryStoringTreeNodeVisitor nodeStore = new InMemoryStoringTreeNodeVisitor();
    final TreeNode node = schema.getDefaultTree();
    final int rootGIndex = 50;
    schema.iterate(nodeStore, 100, rootGIndex, node);
    // LeafNode should be equal
    final TreeNode result = schema.loadBackingNodes(nodeStore, node.hashTreeRoot(), rootGIndex);
    assertThat(result).isEqualTo(node);

    // And should be equal when accessing the actual value
    final SszPrimitive<?, ?> rebuiltPrimitive = schema.createFromBackingNode(result);
    assertThat(rebuiltPrimitive.get()).isEqualTo(schema.createFromBackingNode(node).get());
  }
}
