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

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public final class BeaconBlocksByRangeRequestMessage
    implements RpcRequest, SimpleOffsetSerializable, SSZContainer {
  private final UInt64 startSlot;
  private final UInt64 count;
  private final UInt64 step;

  public BeaconBlocksByRangeRequestMessage(
      final UInt64 startSlot, final UInt64 count, final UInt64 step) {
    this.startSlot = startSlot;
    this.count = count;
    this.step = step;
  }

  @Override
  public int getSSZFieldCount() {
    return 3;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(startSlot.longValue()),
        SSZ.encodeUInt64(count.longValue()),
        SSZ.encodeUInt64(step.longValue()));
  }

  public UInt64 getStartSlot() {
    return startSlot;
  }

  public UInt64 getCount() {
    return count;
  }

  public UInt64 getStep() {
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
    return Objects.equals(startSlot, that.startSlot)
        && Objects.equals(count, that.count)
        && Objects.equals(step, that.step);
  }

  @Override
  public int hashCode() {
    return Objects.hash(startSlot, count, step);
  }
}
