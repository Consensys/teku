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

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.config.Constants;

public class StatusMessage implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  private final Bytes4 forkDigest;
  private final Bytes32 finalizedRoot;
  private final UInt64 finalizedEpoch;
  private final Bytes32 headRoot;
  private final UInt64 headSlot;

  public StatusMessage(
      Bytes4 forkDigest,
      Bytes32 finalizedRoot,
      UInt64 finalizedEpoch,
      Bytes32 headRoot,
      UInt64 headSlot) {
    this.forkDigest = forkDigest;
    this.finalizedRoot = finalizedRoot;
    this.finalizedEpoch = finalizedEpoch;
    this.headRoot = headRoot;
    this.headSlot = headSlot;
  }

  public static StatusMessage createPreGenesisStatus() {
    return new StatusMessage(
        createPreGenesisForkDigest(), Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, UInt64.ZERO);
  }

  private static Bytes4 createPreGenesisForkDigest() {
    final Bytes4 genesisFork = Constants.GENESIS_FORK_VERSION;
    final Bytes32 emptyValidatorsRoot = Bytes32.ZERO;
    return compute_fork_digest(genesisFork, emptyValidatorsRoot);
  }

  @Override
  public int getSSZFieldCount() {
    return 5;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(forkDigest.getWrappedBytes())),
        SSZ.encode(writer -> writer.writeFixedBytes(finalizedRoot)),
        SSZ.encodeUInt64(finalizedEpoch.longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(headRoot)),
        SSZ.encodeUInt64(headSlot.longValue()));
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkDigest, finalizedRoot, finalizedEpoch, headRoot, headSlot);
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
            this.getForkDigest().getWrappedBytes(), other.getForkDigest().getWrappedBytes())
        && Objects.equals(this.getFinalizedRoot(), other.getFinalizedRoot())
        && Objects.equals(this.getFinalizedEpoch(), other.getFinalizedEpoch())
        && Objects.equals(this.getHeadRoot(), other.getHeadRoot())
        && Objects.equals(this.getHeadSlot(), other.getHeadSlot());
  }

  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  public Bytes32 getFinalizedRoot() {
    return finalizedRoot;
  }

  public UInt64 getFinalizedEpoch() {
    return finalizedEpoch;
  }

  public Bytes32 getHeadRoot() {
    return headRoot;
  }

  public UInt64 getHeadSlot() {
    return headSlot;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkDigest", forkDigest)
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
