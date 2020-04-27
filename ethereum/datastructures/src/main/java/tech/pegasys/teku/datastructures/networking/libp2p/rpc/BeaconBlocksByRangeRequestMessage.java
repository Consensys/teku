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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public final class BeaconBlocksByRangeRequestMessage
    implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  private final Bytes32 headBlockRoot;
  private final UnsignedLong startSlot;
  private final UnsignedLong count;
  private final UnsignedLong step;

  public BeaconBlocksByRangeRequestMessage(
      final Bytes32 headBlockRoot,
      final UnsignedLong startSlot,
      final UnsignedLong count,
      final UnsignedLong step) {
    this.headBlockRoot = headBlockRoot;
    this.startSlot = startSlot;
    this.count = count;
    this.step = step;
  }

  @Override
  public int getSSZFieldCount() {
    return 4;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(headBlockRoot)),
        SSZ.encodeUInt64(startSlot.longValue()),
        SSZ.encodeUInt64(count.longValue()),
        SSZ.encodeUInt64(step.longValue()));
  }

  public Bytes32 getHeadBlockRoot() {
    return headBlockRoot;
  }

  public UnsignedLong getStartSlot() {
    return startSlot;
  }

  public UnsignedLong getCount() {
    return count;
  }

  public UnsignedLong getStep() {
    return step;
  }

  @Override
  public int getMaximumRequestChunks() {
    return Math.toIntExact(count.longValue());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconBlocksByRangeRequestMessage that = (BeaconBlocksByRangeRequestMessage) o;
    return Objects.equals(headBlockRoot, that.headBlockRoot)
        && Objects.equals(startSlot, that.startSlot)
        && Objects.equals(count, that.count)
        && Objects.equals(step, that.step);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headBlockRoot, startSlot, count, step);
  }
}
