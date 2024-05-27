/*
 * Copyright Consensys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkNotNull;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.impl.SszVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBitvectorImpl extends SszVectorImpl<SszBit> implements SszBitvector {

  public static SszBitvectorImpl ofBits(final SszBitvectorSchema<?> schema, final int... bits) {
    return new SszBitvectorImpl(schema, new BitvectorImpl(schema.getLength(), bits));
  }

  private final BitvectorImpl value;

  public SszBitvectorImpl(final SszVectorSchema<SszBit, ?> schema, final TreeNode backingNode) {
    super(schema, backingNode);
    value = BitvectorImpl.fromBytes(sszSerialize(), size());
  }

  public SszBitvectorImpl(final SszBitvectorSchema<?> schema, final BitvectorImpl value) {
    super(schema, () -> schema.sszDeserializeTree(SszReader.fromBytes(value.serialize())));
    checkNotNull(value);
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszBitvectorSchema<SszBitvector> getSchema() {
    return (SszBitvectorSchema<SszBitvector>) super.getSchema();
  }

  @Override
  protected IntCache<SszBit> createCache() {
    // BitvectorImpl is far more effective cache than caching individual bits
    return IntCache.noop();
  }

  @Override
  public boolean getBit(final int i) {
    return value.getBit(i);
  }

  @Override
  public int getBitCount() {
    return value.getBitCount();
  }

  @Override
  public SszBitvector rightShift(final int n) {
    return new SszBitvectorImpl(getSchema(), value.rightShift(n));
  }

  @Override
  public IntList getAllSetBits() {
    return IntArrayList.toList(value.streamAllSetBits());
  }

  @Override
  public IntStream streamAllSetBits() {
    return value.streamAllSetBits();
  }

  @Override
  public SszBitvector withBit(final int i) {
    return new SszBitvectorImpl(getSchema(), value.withBit(i));
  }

  @Override
  public SszBitvector or(final SszBitvector other) {
    return new SszBitvectorImpl(getSchema(), value.or(toBitvectorImpl(other)));
  }

  @Override
  public SszBitvector and(SszBitvector other) {
    return new SszBitvectorImpl(getSchema(), value.and(toBitvectorImpl(other)));
  }

  @Override
  protected int sizeImpl() {
    return getSchema().getLength();
  }

  @Override
  public SszMutablePrimitiveVector<Boolean, SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public String toString() {
    return "SszBitvector{size=" + this.size() + ", " + value.toString() + "}";
  }

  private BitvectorImpl toBitvectorImpl(final SszBitvector bv) {
    return ((SszBitvectorImpl) bv).value;
  }
}
