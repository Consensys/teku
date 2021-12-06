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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteListSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszByteListImpl extends SszPrimitiveListImpl<Byte, SszByte> implements SszByteList {

  public SszByteListImpl(SszByteListSchema<?> schema, TreeNode backingTree) {
    super(schema, backingTree);
  }

  @Override
  public Bytes getBytes() {
    MutableBytes bytes = MutableBytes.create(size());
    IntStream.range(0, size()).forEach(idx -> bytes.set(idx, get(idx).get()));
    return bytes;
  }

  @Override
  protected IntCache<SszByte> createCache() {
    // caching with Bytes in this class
    return IntCache.noop();
  }

  @Override
  public SszByteListSchemaImpl<?> getSchema() {
    return (SszByteListSchemaImpl<?>) super.getSchema();
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public String toString() {
    return "SszByteList{" + getBytes() + '}';
  }
}
