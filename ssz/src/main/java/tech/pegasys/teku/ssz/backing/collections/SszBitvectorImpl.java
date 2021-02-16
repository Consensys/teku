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

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableVector;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.cache.NoopIntCache;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszVectorImpl;
import tech.pegasys.teku.ssz.sos.SszReader;

public class SszBitvectorImpl extends SszVectorImpl<SszBit> implements SszBitvector {

  public static SszBitvectorImpl ofBits(SszBitvectorSchema<?> schema, int... bits) {
    return new SszBitvectorImpl(schema, new Bitvector(schema.getLength(), bits));
  }

  private final Bitvector value;

  public SszBitvectorImpl(SszVectorSchema<SszBit, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
    value = toBitvectorImpl(this);
  }

  public SszBitvectorImpl(SszBitvectorSchema<?> schema, Bitvector value) {
    super(schema, () -> schema.sszDeserializeTree(SszReader.fromBytes(value.serialize())));
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszBitvectorSchema<SszBitvector> getSchema() {
    return (SszBitvectorSchema<SszBitvector>) super.getSchema();
  }

  @Override
  protected IntCache<SszBit> createCache() {
    // BitlistImpl is far more effective cache than caching individual bits
    return new NoopIntCache<>();
  }

  private Bitvector toBitvectorImpl(SszBitvector bv) {
    if (bv instanceof SszBitvectorImpl) {
      return ((SszBitvectorImpl) bv).value;
    } else {
      return Bitvector.fromBytes(bv.sszSerialize(), bv.size());
    }
  }

  @Override
  public boolean getBit(int i) {
    return value.getBit(i);
  }

  @Override
  public int getBitCount() {
    return value.getBitCount();
  }

  @Override
  public List<Integer> getAllSetBits() {
    return value.streamAllSetBits().boxed().collect(Collectors.toList());
  }

  @Override
  public SszBitvector withBit(int i) {
    return new SszBitvectorImpl(getSchema(), value.withBit(i));
  }

  @Override
  protected int sizeImpl() {
    return getSchema().getLength();
  }

  private static SszList<SszBit> toSszBitList(
      SszListSchema<SszBit, ?> schema, BitlistImpl bitlist) {
    return schema.sszDeserialize(SszReader.fromBytes(bitlist.serialize()));
  }

  private static BitlistImpl getBitvector(SszList<SszBit> bitlistView) {
    return BitlistImpl.fromSszBytes(
        bitlistView.sszSerialize(), bitlistView.getSchema().getMaxLength());
  }

  @Override
  public SszMutableVector<SszBit> createWritableCopy() {
    throw new UnsupportedOperationException("SszBitlist is immutable structure");
  }

  @Override
  public String toString() {
    return "SszBitlist{size=" + this.size() + ", " + value.toString() + "}";
  }
}
