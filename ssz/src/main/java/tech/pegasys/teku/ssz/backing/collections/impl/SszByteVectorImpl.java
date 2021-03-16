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

package tech.pegasys.teku.ssz.backing.collections.impl;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.collections.SszByteVector;
import tech.pegasys.teku.ssz.backing.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.ssz.backing.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;

public class SszByteVectorImpl extends SszPrimitiveVectorImpl<Byte, SszByte>
    implements SszByteVector {

  private final Bytes data;

  public SszByteVectorImpl(SszByteVectorSchema<?> schema, Bytes bytes) {
    super(schema, () -> SszByteVectorSchemaImpl.fromBytesToTree(schema, bytes));
    this.data = bytes;
  }

  public SszByteVectorImpl(SszByteVectorSchema<?> schema, TreeNode backingTree) {
    super(schema, backingTree);
    this.data = SszByteVectorSchemaImpl.fromTreeToBytes(schema, backingTree);
  }

  @Override
  public Bytes getBytes() {
    return data;
  }

  @Override
  protected IntCache<SszByte> createCache() {
    // caching with Bytes in this class
    return IntCache.noop();
  }

  @Override
  public SszByteVectorSchemaImpl<?> getSchema() {
    return (SszByteVectorSchemaImpl<?>) super.getSchema();
  }

  @Override
  public SszMutablePrimitiveVector<Byte, SszByte> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public String toString() {
    return "SszByteVector{" + data + '}';
  }
}
