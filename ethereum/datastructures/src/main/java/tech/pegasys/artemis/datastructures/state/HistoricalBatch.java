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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class HistoricalBatch implements Copyable<HistoricalBatch>, SimpleOffsetSerializable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private List<Bytes32> block_roots; // Vector bounded by SLOTS_PER_HISTORICAL_ROOT
  private List<Bytes32> state_roots; // Vector bounded by SLOTS_PER_HISTORICAL_ROOT

  public HistoricalBatch(List<Bytes32> block_roots, List<Bytes32> state_roots) {
    this.block_roots = block_roots;
    this.state_roots = state_roots;
  }

  public HistoricalBatch(HistoricalBatch historicalBatch) {
    this.block_roots = copyBytesList(historicalBatch.getBlockRoots(), new ArrayList<>());
    this.state_roots = copyBytesList(historicalBatch.getStateRoots(), new ArrayList<>());
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

  public static HistoricalBatch fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new HistoricalBatch(
                reader.readFixedBytesVector(Constants.SLOTS_PER_HISTORICAL_ROOT, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                reader.readFixedBytesVector(Constants.SLOTS_PER_HISTORICAL_ROOT, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList())));
  }

  @Override
  public HistoricalBatch copy() {
    return new HistoricalBatch(this);
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytesVector(block_roots);
          writer.writeFixedBytesVector(state_roots);
        });
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

  private <T extends List<Bytes32>> T copyBytesList(T sourceList, T destinationList) {
    for (Bytes sourceItem : sourceList) {
      destinationList.add((Bytes32) sourceItem.copy());
    }
    return destinationList;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Bytes32> getBlockRoots() {
    return block_roots;
  }

  public void setBlockRoots(List<Bytes32> block_roots) {
    this.block_roots = block_roots;
  }

  public List<Bytes32> getStateRoots() {
    return state_roots;
  }

  public void setStateRoots(List<Bytes32> state_roots) {
    this.state_roots = state_roots;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_COMPOSITE, block_roots),
            HashTreeUtil.hash_tree_root(SSZTypes.TUPLE_OF_COMPOSITE, state_roots)));
  }
}
