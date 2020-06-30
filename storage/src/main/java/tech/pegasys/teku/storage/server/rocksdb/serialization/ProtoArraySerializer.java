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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import static java.util.stream.Collectors.toList;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoNode;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArraySerializer implements RocksDbSerializer<ProtoArray> {
  @Override
  public ProtoArray deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final UnsignedLong justifiedEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
          final UnsignedLong finalizedEpoch = UnsignedLong.fromLongBits(reader.readUInt64());
          final List<BlockInformation> blockInformationList =
              reader.readBytesList().stream().map(BlockInformation::fromBytes).collect(toList());
          ProtoArray protoArray =
              new ProtoArray(
                  Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
                  justifiedEpoch,
                  finalizedEpoch,
                  new ArrayList<>(),
                  new HashMap<>());
          blockInformationList.forEach(
              blockInformation ->
                  protoArray.onBlock(
                      blockInformation.blockSlot,
                      blockInformation.blockRoot,
                      blockInformation.parentRoot,
                      blockInformation.stateRoot,
                      blockInformation.justifiedEpoch,
                      blockInformation.finalizedEpoch));
          return protoArray;
        });
  }

  @Override
  public byte[] serialize(final ProtoArray protoArray) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeUInt64(protoArray.getJustifiedEpoch().longValue());
              writer.writeUInt64(protoArray.getFinalizedEpoch().longValue());
              writer.writeBytesList(
                  protoArray.getNodes().stream().map(BlockInformation::toBytes).collect(toList()));
            });
    return bytes.toArrayUnsafe();
  }

  private static class BlockInformation {
    private final UnsignedLong blockSlot;
    private final Bytes32 blockRoot;
    private final Bytes32 parentRoot;
    private final Bytes32 stateRoot;
    private final UnsignedLong justifiedEpoch;
    private final UnsignedLong finalizedEpoch;

    private BlockInformation(
        UnsignedLong blockSlot,
        Bytes32 blockRoot,
        Bytes32 parentRoot,
        Bytes32 stateRoot,
        UnsignedLong justifiedEpoch,
        UnsignedLong finalizedEpoch) {
      this.blockSlot = blockSlot;
      this.blockRoot = blockRoot;
      this.parentRoot = parentRoot;
      this.stateRoot = stateRoot;
      this.justifiedEpoch = justifiedEpoch;
      this.finalizedEpoch = finalizedEpoch;
    }

    public static Bytes toBytes(ProtoNode node) {
      return SSZ.encode(
          writer -> {
            writer.writeUInt64(node.getBlockSlot().longValue());
            writer.writeFixedBytes(node.getBlockRoot());
            writer.writeFixedBytes(node.getParentRoot());
            writer.writeFixedBytes(node.getStateRoot());
            writer.writeUInt64(node.getJustifiedEpoch().longValue());
            writer.writeUInt64(node.getFinalizedEpoch().longValue());
          });
    }

    public static BlockInformation fromBytes(Bytes data) {
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
  }
}
