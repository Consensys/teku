/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.protoarray;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;

public class BlockInformation {
  private final UnsignedLong blockSlot;
  private final Bytes32 blockRoot;
  private final Bytes32 parentRoot;
  private final Bytes32 stateRoot;
  private final UnsignedLong justifiedEpoch;
  private final UnsignedLong finalizedEpoch;

  public BlockInformation(
      final UnsignedLong blockSlot,
      final Bytes32 blockRoot,
      final Bytes32 parentRoot,
      final Bytes32 stateRoot,
      final UnsignedLong justifiedEpoch,
      final UnsignedLong finalizedEpoch) {
    this.blockSlot = blockSlot;
    this.blockRoot = blockRoot;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.justifiedEpoch = justifiedEpoch;
    this.finalizedEpoch = finalizedEpoch;
  }

  public ProtoNode toProtoNode() {
    return new ProtoNode(
        blockSlot,
        stateRoot,
        blockRoot,
        parentRoot,
        Optional.empty(),
        justifiedEpoch,
        finalizedEpoch,
        UnsignedLong.ZERO,
        Optional.empty(),
        Optional.empty());
  }

  public static Bytes toBytes(final BlockInformation blockInformation) {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(blockInformation.getBlockSlot().longValue());
          writer.writeFixedBytes(blockInformation.getBlockRoot());
          writer.writeFixedBytes(blockInformation.getParentRoot());
          writer.writeFixedBytes(blockInformation.getStateRoot());
          writer.writeUInt64(blockInformation.getJustifiedEpoch().longValue());
          writer.writeUInt64(blockInformation.getFinalizedEpoch().longValue());
        });
  }

  public static BlockInformation fromBytes(final Bytes data) {
    return SSZ.decode(
        data,
        reader -> {
          final UnsignedLong blockSlot = UnsignedLong.fromLongBits(reader.readUInt64());
          final Bytes32 blockRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 parentRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 stateRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final UnsignedLong justifiedEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
          final UnsignedLong finalizedEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
          return new BlockInformation(
              blockSlot, blockRoot, parentRoot, stateRoot, justifiedEpoch, finalizedEpoch);
        });
  }

  public UnsignedLong getBlockSlot() {
    return blockSlot;
  }

  public Bytes32 getBlockRoot() {
    return blockRoot;
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }

  public Bytes32 getStateRoot() {
    return stateRoot;
  }

  public UnsignedLong getJustifiedEpoch() {
    return justifiedEpoch;
  }

  public UnsignedLong getFinalizedEpoch() {
    return finalizedEpoch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BlockInformation)) return false;
    BlockInformation that = (BlockInformation) o;
    return Objects.equal(getBlockSlot(), that.getBlockSlot())
        && Objects.equal(getBlockRoot(), that.getBlockRoot())
        && Objects.equal(getParentRoot(), that.getParentRoot())
        && Objects.equal(getStateRoot(), that.getStateRoot())
        && Objects.equal(getJustifiedEpoch(), that.getJustifiedEpoch())
        && Objects.equal(getFinalizedEpoch(), that.getFinalizedEpoch());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getBlockSlot(),
        getBlockRoot(),
        getParentRoot(),
        getStateRoot(),
        getJustifiedEpoch(),
        getFinalizedEpoch());
  }
}
