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

package tech.pegasys.artemis.datastructures.networking.libp2p.rpc;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public final class BeaconBlocksMessageRequest implements SimpleOffsetSerializable, SSZContainer {

  private final Bytes32 headBlockRoot;
  private final UnsignedLong startSlot;
  private final UnsignedLong count;
  private final UnsignedLong step;

  public BeaconBlocksMessageRequest(
      Bytes32 headBlockRoot, UnsignedLong startSlot, UnsignedLong count, UnsignedLong step) {
    this.headBlockRoot = headBlockRoot;
    this.startSlot = startSlot;
    this.step = step;
    this.count = count;
  }

  @Override
  public int getSSZFieldCount() {
    return 4;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList =
        new ArrayList<>(
            List.of(
                SSZ.encode(writer -> writer.writeFixedBytes(headBlockRoot)),
                SSZ.encodeUInt64(startSlot.longValue()),
                SSZ.encodeUInt64(count.longValue()),
                SSZ.encodeUInt64(step.longValue())));
    return fixedPartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(headBlockRoot, startSlot, count, step);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlocksMessageRequest)) {
      return false;
    }

    BeaconBlocksMessageRequest other = (BeaconBlocksMessageRequest) obj;
    return Objects.equals(this.getHeadBlockRoot(), other.getHeadBlockRoot())
        && Objects.equals(this.getStartSlot(), other.getStartSlot())
        && Objects.equals(this.getCount(), other.getCount())
        && Objects.equals(this.getStep(), other.getStep());
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
}
