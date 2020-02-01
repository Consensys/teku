/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.state;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class HistoricalBatch
    implements Merkleizable, Copyable<HistoricalBatch>, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private final SSZVector<Bytes32> block_roots; // Vector bounded by SLOTS_PER_HISTORICAL_ROOT
  private final SSZVector<Bytes32> state_roots; // Vector bounded by SLOTS_PER_HISTORICAL_ROOT

  public HistoricalBatch(SSZVector<Bytes32> block_roots, SSZVector<Bytes32> state_roots) {
    this.block_roots = block_roots;
    this.state_roots = state_roots;
  }

  public HistoricalBatch() {
    this.block_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO);
    this.state_roots = new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO);
  }

  public HistoricalBatch(HistoricalBatch historicalBatch) {
    this.block_roots =
        copyBytesList(
            historicalBatch.getBlockRoots(),
            new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO));
    this.state_roots =
        copyBytesList(
            historicalBatch.getStateRoots(),
            new SSZVector<>(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO));
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytesVector(block_roots)),
        SSZ.encode(writer -> writer.writeFixedBytesVector(state_roots)));
  }

  @Override
  public HistoricalBatch copy() {
    return new HistoricalBatch(this);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block_roots, state_roots);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof HistoricalBatch)) {
      return false;
    }

    HistoricalBatch other = (HistoricalBatch) obj;
    return Objects.equals(this.getBlockRoots(), other.getBlockRoots())
        && Objects.equals(this.getStateRoots(), other.getStateRoots());
  }

  private <T extends SSZVector<Bytes32>> T copyBytesList(T sourceList, T destinationList) {
    for (Bytes sourceItem : sourceList) {
      destinationList.add((Bytes32) sourceItem.copy());
    }
    return destinationList;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SSZVector<Bytes32> getBlockRoots() {
    return block_roots;
  }

  public SSZVector<Bytes32> getStateRoots() {
    return state_roots;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, block_roots),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, state_roots)));
  }
}
