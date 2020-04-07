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

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;
import tech.pegasys.artemis.ssz.SSZTypes.SSZContainer;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;

public class StatusMessage implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  private final Bytes4 headForkVersion;
  private final Bytes32 finalizedRoot;
  private final UnsignedLong finalizedEpoch;
  private final Bytes32 headRoot;
  private final UnsignedLong headSlot;

  public StatusMessage(
      Bytes4 headForkVersion,
      Bytes32 finalizedRoot,
      UnsignedLong finalizedEpoch,
      Bytes32 headRoot,
      UnsignedLong headSlot) {
    this.headForkVersion = headForkVersion;
    this.finalizedRoot = finalizedRoot;
    this.finalizedEpoch = finalizedEpoch;
    this.headRoot = headRoot;
    this.headSlot = headSlot;
  }

  @Override
  public int getSSZFieldCount() {
    return 5;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(headForkVersion.getWrappedBytes())),
        SSZ.encode(writer -> writer.writeFixedBytes(finalizedRoot)),
        SSZ.encodeUInt64(finalizedEpoch.longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(headRoot)),
        SSZ.encodeUInt64(headSlot.longValue()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(headForkVersion, finalizedRoot, finalizedEpoch, headRoot, headSlot);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof StatusMessage)) {
      return false;
    }

    StatusMessage other = (StatusMessage) obj;
    return Objects.equals(
            this.getHeadForkVersion().getWrappedBytes(),
            other.getHeadForkVersion().getWrappedBytes())
        && Objects.equals(this.getFinalizedRoot(), other.getFinalizedRoot())
        && Objects.equals(this.getFinalizedEpoch(), other.getFinalizedEpoch())
        && Objects.equals(this.getHeadRoot(), other.getHeadRoot())
        && Objects.equals(this.getHeadSlot(), other.getHeadSlot());
  }

  public Bytes4 getHeadForkVersion() {
    return headForkVersion;
  }

  public Bytes32 getFinalizedRoot() {
    return finalizedRoot;
  }

  public UnsignedLong getFinalizedEpoch() {
    return finalizedEpoch;
  }

  public Bytes32 getHeadRoot() {
    return headRoot;
  }

  public UnsignedLong getHeadSlot() {
    return headSlot;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkVersion", headForkVersion)
        .add("finalizedRoot", finalizedRoot)
        .add("finalizedEpoch", finalizedEpoch)
        .add("headRoot", headRoot)
        .add("headSlot", headSlot)
        .toString();
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}
