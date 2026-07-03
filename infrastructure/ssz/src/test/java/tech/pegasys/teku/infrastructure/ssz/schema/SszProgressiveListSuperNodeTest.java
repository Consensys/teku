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

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.ProgressiveTreeUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszProgressiveListSuperNodeTest {

  private static final int SUPER_NODE_DEPTH = 4; // 16 elements per supernode
  // Fixed-size composite element: uint64 + bytes32 = 40 bytes
  private static final SszContainerSchema<SszContainer> ELEMENT_SCHEMA =
      SszContainerSchema.create(
          "FixedElem",
          List.of(
              NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
              NamedSchema.of("b", SszPrimitiveSchemas.BYTES32_SCHEMA)),
          SszContainerImpl::new);

  private static final SszProgressiveListSchema<SszContainer> PLAIN_SCHEMA =
      SszProgressiveListSchema.create(ELEMENT_SCHEMA);
  private static final SszProgressiveListSchema<SszContainer> HINTED_SCHEMA =
      SszProgressiveListSchema.create(
          ELEMENT_SCHEMA, SszSchemaHints.sszSuperNode(SUPER_NODE_DEPTH));

  private static SszContainer element(final int i) {
    return ELEMENT_SCHEMA.createFromBackingNode(
        ELEMENT_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(i)),
                SszBytes32.of(Hash.sha256(Bytes.ofUnsignedInt(i))))));
  }

  private static List<SszContainer> elements(final int count) {
    final List<SszContainer> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      result.add(element(i));
    }
    return result;
  }

  /**
   * Builds a packed hinted list through SSZ deserialization — the only construction path that packs
   * (createFromElements is unhinted by design). Also used by Tasks 3 and 4.
   */
  private static SszList<SszContainer> hintedFromSsz(final int size) {
    return HINTED_SCHEMA.sszDeserialize(
        PLAIN_SCHEMA.createFromElements(elements(size)).sszSerialize());
  }

  // sizes crossing level boundaries (1, 5, 21, 85) and supernode boundaries (16, 17, 32)
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 5, 16, 17, 21, 32, 85, 100, 341, 500})
  void createFromElements_hintedMatchesPlain(final int size) {
    // createFromElements intentionally builds a plain tree on hinted schemas: this test
    // pins hash/serialization parity only, no representation guarantee
    final SszList<SszContainer> plain = PLAIN_SCHEMA.createFromElements(elements(size));
    final SszList<SszContainer> hinted = HINTED_SCHEMA.createFromElements(elements(size));
    assertThat(hinted.hashTreeRoot()).isEqualTo(plain.hashTreeRoot());
    assertThat(hinted.sszSerialize()).isEqualTo(plain.sszSerialize());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 5, 21, 85, 341, 500})
  void sszDeserialize_hintedMatchesPlain(final int size) {
    final Bytes ssz = PLAIN_SCHEMA.createFromElements(elements(size)).sszSerialize();
    final SszList<SszContainer> hinted = HINTED_SCHEMA.sszDeserialize(ssz);
    final SszList<SszContainer> plain = PLAIN_SCHEMA.sszDeserialize(ssz);
    assertThat(hinted.hashTreeRoot()).isEqualTo(plain.hashTreeRoot());
    assertThat(hinted.sszSerialize()).isEqualTo(ssz);
    // element reads through the supernode
    for (int i = 0; i < size; i++) {
      assertThat(hinted.get(i).hashTreeRoot()).isEqualTo(plain.get(i).hashTreeRoot());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 5, 21, 85, 341})
  void sszDeserialize_backingTreeUsesSuperNodes(final int size) {
    assertLevelsAreSuperNodeBacked(hintedFromSsz(size).getBackingNode(), size);
  }

  @Test
  void hintIgnoredForPrimitiveElements() {
    final SszProgressiveListSchema<SszUInt64> hintedPrimitive =
        SszProgressiveListSchema.create(
            SszPrimitiveSchemas.UINT64_SCHEMA, SszSchemaHints.sszSuperNode(SUPER_NODE_DEPTH));
    final List<SszUInt64> values =
        List.of(SszUInt64.of(UInt64.valueOf(1)), SszUInt64.of(UInt64.valueOf(2)));
    final SszList<SszUInt64> list = hintedPrimitive.createFromElements(values);
    final SszList<SszUInt64> plain =
        SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)
            .createFromElements(values);
    assertThat(list.hashTreeRoot()).isEqualTo(plain.hashTreeRoot());
    // no supernodes anywhere for primitives: level 0 chunk is a plain leaf
    final TreeNode level0 =
        list.getBackingNode()
            .get(
                GIndexUtil.gIdxCompose(
                    GIndexUtil.LEFT_CHILD_G_INDEX, ProgressiveTreeUtil.spineGIndex(0)));
    assertThat(level0).isNotInstanceOf(SszSuperNode.class);
  }

  /**
   * Walks each level of the progressive data tree and asserts: level 0 is a plain element node;
   * every level L >= 1 has SszSuperNode bottom nodes of depth min(2L, SUPER_NODE_DEPTH).
   */
  static void assertLevelsAreSuperNodeBacked(final TreeNode backingNode, final int size) {
    final TreeNode dataTree = backingNode.get(GIndexUtil.LEFT_CHILD_G_INDEX);
    final int maxLevel = ProgressiveTreeUtil.levelForIndex(size - 1);
    for (int level = 0; level <= maxLevel; level++) {
      final TreeNode levelSubtree = dataTree.get(ProgressiveTreeUtil.spineGIndex(level));
      final int depth = ProgressiveTreeUtil.levelDepth(level);
      if (level == 0) {
        assertThat(levelSubtree).isNotInstanceOf(SszSuperNode.class);
        continue;
      }
      final int sl = Math.min(depth, SUPER_NODE_DEPTH);
      // first supernode of the level (leftmost node at depth (depth - sl) within the level)
      TreeNode node = levelSubtree;
      for (int d = 0; d < depth - sl; d++) {
        node = node.get(GIndexUtil.LEFT_CHILD_G_INDEX);
      }
      assertThat(node).describedAs("level %s bottom node", level).isInstanceOf(SszSuperNode.class);
    }
  }
}
