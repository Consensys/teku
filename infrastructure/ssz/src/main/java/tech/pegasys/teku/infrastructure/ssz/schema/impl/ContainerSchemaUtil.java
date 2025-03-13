/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszType.SSZ_LENGTH_SIZE;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszType;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ContainerSchemaUtil {
  static void deserializeFixedChild(
      final SszReader reader,
      final Queue<TreeNode> fixedChildrenSubtrees,
      final IntList variableChildrenOffsets,
      final SszCompositeSchema<?> containerSchema,
      final int index) {
    final SszSchema<?> childType = containerSchema.getChildSchema(index);
    if (childType.isFixedSize()) {
      try (SszReader sszReader = reader.slice(childType.getSszFixedPartSize())) {
        final TreeNode childNode = childType.sszDeserializeTree(sszReader);
        fixedChildrenSubtrees.add(childNode);
      }
    } else {
      final int childOffset = SszType.sszBytesToLength(reader.read(SSZ_LENGTH_SIZE));
      variableChildrenOffsets.add(childOffset);
    }
  }

  static ArrayDeque<Integer> validateAndPrepareForVariableChildrenDeserialization(
      final SszReader reader, final IntList variableChildrenOffsets, final int endOffset) {

    if (variableChildrenOffsets.isEmpty()) {
      if (reader.getAvailableBytes() > 0) {
        throw new SszDeserializeException("Invalid SSZ: unread bytes for fixed size container");
      }
    } else {
      if (variableChildrenOffsets.getInt(0) != endOffset - reader.getAvailableBytes()) {
        throw new SszDeserializeException(
            "First variable element offset doesn't match the end of fixed part");
      }
    }

    variableChildrenOffsets.add(endOffset);

    final ArrayDeque<Integer> variableChildrenSizes =
        new ArrayDeque<>(variableChildrenOffsets.size() - 1);
    for (int i = 0; i < variableChildrenOffsets.size() - 1; i++) {
      variableChildrenSizes.add(
          variableChildrenOffsets.getInt(i + 1) - variableChildrenOffsets.getInt(i));
    }

    if (variableChildrenSizes.stream().anyMatch(s -> s < 0)) {
      throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
    }

    return variableChildrenSizes;
  }

  static void deserializeVariableChild(
      final SszReader reader,
      final List<TreeNode> childrenSubtrees,
      final Queue<TreeNode> fixedChildrenSubtrees,
      final ArrayDeque<Integer> variableChildrenSizes,
      final SszCompositeSchema<?> containerSchema,
      final int index) {
    final SszSchema<?> childType = containerSchema.getChildSchema(index);
    if (childType.isFixedSize()) {
      childrenSubtrees.add(fixedChildrenSubtrees.remove());
    } else {
      try (SszReader sszReader = reader.slice(variableChildrenSizes.remove())) {
        TreeNode childNode = childType.sszDeserializeTree(sszReader);
        childrenSubtrees.add(childNode);
      }
    }
  }

  /**
   * @return variable child size, 0 if it is fixed.
   */
  static int serializeFixedChild(
      final SszWriter writer,
      final SszCompositeSchema<?> containerSchema,
      final int index,
      final TreeNode node,
      final int[] variableSizes,
      final int variableChildOffset) {
    final TreeNode childSubtree = node.get(containerSchema.getChildGeneralizedIndex(index));
    final SszSchema<?> childType = containerSchema.getChildSchema(index);
    if (childType.isFixedSize()) {
      final int size = childType.sszSerializeTree(childSubtree, writer);
      assert size == childType.getSszFixedPartSize();
      return 0;
    }
    writer.write(SszType.sszLengthToBytes(variableChildOffset));
    final int variableChildSize = childType.getSszSize(childSubtree);
    variableSizes[index] = variableChildSize;
    return variableChildSize;
  }

  static void serializeVariableChild(
      final SszWriter writer,
      final SszCompositeSchema<?> containerSchema,
      final int index,
      final int[] variableSizes,
      final TreeNode node) {
    final SszSchema<?> childType = containerSchema.getChildSchema(index);
    if (!childType.isFixedSize()) {
      TreeNode childSubtree = node.get(containerSchema.getChildGeneralizedIndex(index));
      int size = childType.sszSerializeTree(childSubtree, writer);
      assert size == variableSizes[index];
    }
  }
}
