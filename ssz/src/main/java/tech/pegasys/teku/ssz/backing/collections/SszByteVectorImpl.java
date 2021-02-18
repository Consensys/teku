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

package tech.pegasys.teku.ssz.backing.collections;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;

public class SszByteVectorImpl extends SszPrimitiveVectorImpl<Byte, SszByte>
    implements SszByteVector {

  public SszByteVectorImpl(SszByteVectorSchema<?> schema, Supplier<TreeNode> lazyBackingNode) {
    super(schema, lazyBackingNode);
  }

  public SszByteVectorImpl(SszByteVectorSchema<?> schema, Bytes bytes) {
    super(null, null);
    throw new UnsupportedOperationException("TODO");
  }
}
