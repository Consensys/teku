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
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SszBitlistImpl extends SszListImpl<SszBit> implements SszBitlist {

  private final Bitlist value;

  public SszBitlistImpl(SszListSchema<SszBit, ?> schema, TreeNode backingNode) {
    super(schema, backingNode);
    value = SszUtils.getBitlist(this);
  }

  public SszBitlistImpl(SszListSchema<SszBit, ?> schema, Bitlist value) {
    super(schema, SszUtils.toSszBitList(value).getBackingNode());
    this.value = value;
  }

  @SuppressWarnings("unchecked")
  @Override
  public SszBitlistSchema<SszBitlist> getSchema() {
    return (SszBitlistSchema<SszBitlist>) super.getSchema();
  }

  public Bitlist toLegacy(SszBitlist bl) {
    if (bl instanceof SszBitlistImpl) {
      return ((SszBitlistImpl) bl).value;
    } else {
      throw new UnsupportedOperationException("TODO");
    }
  }

  @Override
  public SszBitlist or(SszBitlist other) {
    return new SszBitlistImpl(getSchema(), value.or(toLegacy(other)));
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
  public boolean intersects(SszBitlist other) {
    return value.intersects(toLegacy(other));
  }

  @Override
  public boolean isSuperSetOf(SszBitlist other) {
    return value.isSuperSetOf(toLegacy(other));
  }

  @Override
  public List<Integer> getAllSetBits() {
    return value.getAllSetBits();
  }

  @Override
  public IntStream streamAllSetBits() {
    return value.streamAllSetBits();
  }

  @Override
  public int getSize() {
    return value.getCurrentSize();
  }
}
