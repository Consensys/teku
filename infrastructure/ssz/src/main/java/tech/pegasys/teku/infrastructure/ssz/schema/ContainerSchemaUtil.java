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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Shared helpers for SSZ container schemas. Extracts the identical serialization, deserialization,
 * and computed-property logic used by both {@code AbstractSszContainerSchema} and {@code
 * SszProgressiveContainerSchema}.
 */
public class ContainerSchemaUtil {

  private ContainerSchemaUtil() {}

  public static boolean isFixedSize(final SszContainerSchema<?> schema) {
    for (int i = 0; i < schema.getFieldsCount(); i++) {
      if (!schema.getChildSchema(i).isFixedSize()) {
        return false;
      }
    }
    return true;
  }

  public static int calcSszFixedPartSize(final SszContainerSchema<?> schema) {
    int size = 0;
    for (int i = 0; i < schema.getFieldsCount(); i++) {
      SszSchema<?> childType = schema.getChildSchema(i);
      size += childType.isFixedSize() ? childType.getSszFixedPartSize() : SszType.SSZ_LENGTH_SIZE;
    }
    return size;
  }

  public static int getSszVariablePartSize(
      final SszContainerSchema<?> schema, final TreeNode node) {
    if (schema.isFixedSize()) {
      return 0;
    }
    int size = 0;
    for (int i = 0; i < schema.getFieldsCount(); i++) {
      SszSchema<?> childType = schema.getChildSchema(i);
      if (!childType.isFixedSize()) {
        size += childType.getSszSize(node.get(schema.getChildGeneralizedIndex(i)));
      }
    }
    return size;
  }

  public static int sszSerializeTree(
      final SszContainerSchema<?> schema,
      final TreeNode node,
      final SszWriter writer,
      final int fixedPartSize) {
    int variableChildOffset = fixedPartSize;
    int[] variableSizes = new int[schema.getFieldsCount()];
    for (int i = 0; i < schema.getFieldsCount(); i++) {
      TreeNode childSubtree = node.get(schema.getChildGeneralizedIndex(i));
      SszSchema<?> childType = schema.getChildSchema(i);
      if (childType.isFixedSize()) {
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == childType.getSszFixedPartSize();
      } else {
        writer.write(SszType.sszLengthToBytes(variableChildOffset));
        int childSize = childType.getSszSize(childSubtree);
        variableSizes[i] = childSize;
        variableChildOffset += childSize;
      }
    }
    for (int i = 0; i < schema.getFieldsCount(); i++) {
      SszSchema<?> childType = schema.getChildSchema(i);
      if (!childType.isFixedSize()) {
        TreeNode childSubtree = node.get(schema.getChildGeneralizedIndex(i));
        int size = childType.sszSerializeTree(childSubtree, writer);
        assert size == variableSizes[i];
      }
    }
    return variableChildOffset;
  }

  public static TreeNode sszDeserializeTree(
      final SszContainerSchema<?> schema,
      final SszReader reader,
      final Function<List<TreeNode>, TreeNode> treeAssembler) {
    int endOffset = reader.getAvailableBytes();
    int childCount = schema.getFieldsCount();
    Queue<TreeNode> fixedChildrenSubtrees = new ArrayDeque<>(childCount);
    IntList variableChildrenOffsets = new IntArrayList(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = schema.getChildSchema(i);
      if (childType.isFixedSize()) {
        try (SszReader sszReader = reader.slice(childType.getSszFixedPartSize())) {
          fixedChildrenSubtrees.add(childType.sszDeserializeTree(sszReader));
        }
      } else {
        int childOffset = SszType.sszBytesToLength(reader.read(SszType.SSZ_LENGTH_SIZE));
        variableChildrenOffsets.add(childOffset);
      }
    }

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

    ArrayDeque<Integer> variableChildrenSizes =
        new ArrayDeque<>(variableChildrenOffsets.size() - 1);
    for (int i = 0; i < variableChildrenOffsets.size() - 1; i++) {
      variableChildrenSizes.add(
          variableChildrenOffsets.getInt(i + 1) - variableChildrenOffsets.getInt(i));
    }

    if (variableChildrenSizes.stream().anyMatch(s -> s < 0)) {
      throw new SszDeserializeException("Invalid SSZ: wrong child offsets");
    }

    List<TreeNode> fieldNodes = new ArrayList<>(childCount);
    for (int i = 0; i < childCount; i++) {
      SszSchema<?> childType = schema.getChildSchema(i);
      if (childType.isFixedSize()) {
        fieldNodes.add(fixedChildrenSubtrees.remove());
      } else {
        try (SszReader sszReader = reader.slice(variableChildrenSizes.remove())) {
          fieldNodes.add(childType.sszDeserializeTree(sszReader));
        }
      }
    }

    return treeAssembler.apply(fieldNodes);
  }

  public static SszLengthBounds computeSszLengthBounds(final SszContainerSchema<?> schema) {
    return IntStream.range(0, schema.getFieldsCount())
        .mapToObj(schema::getChildSchema)
        .map(t -> t.getSszLengthBounds().addBytes(t.isFixedSize() ? 0 : SszType.SSZ_LENGTH_SIZE))
        .map(SszLengthBounds::ceilToBytes)
        .reduce(SszLengthBounds.ZERO, SszLengthBounds::add);
  }
}
