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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszByteListSchemaImpl<SszListT extends SszByteList>
    extends AbstractSszListSchema<SszByte, SszListT> implements SszByteListSchema<SszListT> {

  public SszByteListSchemaImpl(long maxLength) {
    super(SszPrimitiveSchemas.BYTE_SCHEMA, maxLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(TreeNode node) {
    return (SszListT) new SszByteListImpl(this, node);
  }

  @Override
  public SszListT fromBytes(Bytes bytes) {
    checkArgument(bytes.size() <= getMaxLength(), "Bytes size greater than list max length");
    TreeNode dataTreeNode = SchemaUtils.createTreeFromBytes(bytes, treeDepth());
    TreeNode listTreeNode = createTree(dataTreeNode, bytes.size());
    return createFromBackingNode(listTreeNode);
  }

  @Override
  public SszListT createFromElements(List<? extends SszByte> elements) {
    Bytes bytes = Bytes.of(elements.stream().mapToInt(sszByte -> 0xFF & sszByte.get()).toArray());
    return fromBytes(bytes);
  }
}
