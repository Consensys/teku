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
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszByteVectorSchemaImpl<SszVectorT extends SszByteVector>
    extends AbstractSszVectorSchema<SszByte, SszVectorT>
    implements SszByteVectorSchema<SszVectorT> {

  public SszByteVectorSchemaImpl(long vectorLength) {
    super(SszPrimitiveSchemas.BYTE_SCHEMA, vectorLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT createFromBackingNode(TreeNode node) {
    return (SszVectorT) new SszByteVectorImpl(this, node);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT fromBytes(Bytes bytes) {
    return (SszVectorT) new SszByteVectorImpl(this, bytes);
  }

  public static TreeNode fromBytesToTree(SszByteVectorSchema<?> schema, Bytes bytes) {
    checkArgument(bytes.size() == schema.getLength(), "Bytes size doesn't match vector length");
    return SchemaUtils.createTreeFromBytes(bytes, schema.treeDepth());
  }

  public static Bytes fromTreeToBytes(SszByteVectorSchema<?> schema, TreeNode tree) {
    Bytes bytes = TreeUtil.concatenateLeavesData(tree);
    checkArgument(bytes.size() == schema.getLength(), "Tree doesn't match vector schema");
    return bytes;
  }

  @Override
  public SszVectorT createFromElements(List<? extends SszByte> elements) {
    Bytes bytes = Bytes.of(elements.stream().mapToInt(sszByte -> 0xFF & sszByte.get()).toArray());
    return fromBytes(bytes);
  }
}
