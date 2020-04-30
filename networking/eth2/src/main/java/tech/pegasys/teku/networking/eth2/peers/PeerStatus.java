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

package tech.pegasys.teku.networking.eth2.peers;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class PeerStatus {
  private static final PeerStatus PREGENESIS_STATUS = createPreGenesisStatus();

  private final Bytes4 forkDigest;
  private final Checkpoint finalizedCheckpoint;
  private final Bytes32 headRoot;
  private final UnsignedLong headSlot;

  public static PeerStatus fromStatusMessage(final StatusMessage message) {
    return new PeerStatus(
        message.getForkDigest().copy(),
        message.getFinalizedRoot().copy(),
        message.getFinalizedEpoch(),
        message.getHeadRoot().copy(),
        message.getHeadSlot());
  }

  public static PeerStatus createPreGenesisStatus() {
    return fromStatusMessage(StatusMessage.createPreGenesisStatus());
  }

  public static boolean isPreGenesisStatus(final PeerStatus status) {
    checkNotNull(status);
    return Objects.equals(status, PREGENESIS_STATUS);
  }

  PeerStatus(
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UnsignedLong finalizedEpoch,
      final Bytes32 headRoot,
      final UnsignedLong headSlot) {
    this.forkDigest = forkDigest;
    this.finalizedCheckpoint = new Checkpoint(finalizedEpoch, finalizedRoot);
    this.headRoot = headRoot;
    this.headSlot = headSlot;
  }

  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  public Bytes32 getFinalizedRoot() {
    return finalizedCheckpoint.getRoot();
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public UnsignedLong getFinalizedEpoch() {
    return finalizedCheckpoint.getEpoch();
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
        .add("forkDigest", forkDigest)
        .add("finalizedRoot", getFinalizedRoot())
        .add("finalizedEpoch", getFinalizedEpoch())
        .add("headRoot", headRoot)
        .add("headSlot", headSlot)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PeerStatus)) {
      return false;
    }
    final PeerStatus that = (PeerStatus) o;
    return Objects.equals(forkDigest, that.forkDigest)
        && Objects.equals(finalizedCheckpoint, that.finalizedCheckpoint)
        && Objects.equals(headRoot, that.headRoot)
        && Objects.equals(headSlot, that.headSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkDigest, finalizedCheckpoint, headRoot, headSlot);
  }
}
