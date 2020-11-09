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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.BlockInformation;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArraySnapshotSerializer implements RocksDbSerializer<ProtoArraySnapshot> {
  @Override
  public ProtoArraySnapshot deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final UInt64 justifiedEpoch = UInt64.fromLongBits(reader.readUInt64());
          final UInt64 finalizedEpoch = UInt64.fromLongBits(reader.readUInt64());
          final List<BlockInformation> blockInformationList =
              reader.readBytesList().stream().map(BlockInformation::fromBytes).collect(toList());

          final UInt64 initialEpoch;
          if (reader.isComplete()) {
            // Read earlier format which is missing an explicit initial epoch value
            // Set initial epoch to default of zero for genesis
            initialEpoch = UInt64.valueOf(Constants.GENESIS_EPOCH);
          } else {
            // Read initial epoch
            initialEpoch = UInt64.fromLongBits(reader.readUInt64());
          }

          return new ProtoArraySnapshot(
              justifiedEpoch, finalizedEpoch, initialEpoch, blockInformationList);
        });
  }

  @Override
  public byte[] serialize(final ProtoArraySnapshot protoArraySnapshot) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeUInt64(protoArraySnapshot.getJustifiedEpoch().longValue());
              writer.writeUInt64(protoArraySnapshot.getFinalizedEpoch().longValue());
              writer.writeBytesList(
                  protoArraySnapshot.getBlockInformationList().stream()
                      .map(BlockInformation::toBytes)
                      .collect(toList()));
              writer.writeUInt64(protoArraySnapshot.getInitialEpoch().longValue());
            });
    return bytes.toArrayUnsafe();
  }
}
