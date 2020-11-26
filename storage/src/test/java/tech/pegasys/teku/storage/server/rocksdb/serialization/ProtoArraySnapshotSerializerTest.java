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
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.BlockInformation;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;

public class ProtoArraySnapshotSerializerTest {
  private final ProtoArraySnapshotSerializer serializer = new ProtoArraySnapshotSerializer();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void shouldEncodeAndDecodeWithMultipleBlocks() {
    List<BlockInformation> blockInformationList = new ArrayList<>();

    BeaconBlock block1 = dataStructureUtil.randomBeaconBlock(10000);
    BeaconBlock block2 = dataStructureUtil.randomBeaconBlock(10001);
    BeaconBlock block3 = dataStructureUtil.randomBeaconBlock(10002);

    addBlockToBlockInformationList(blockInformationList, block1);
    addBlockToBlockInformationList(blockInformationList, block2);
    addBlockToBlockInformationList(blockInformationList, block3);

    ProtoArraySnapshot protoArraySnapshot =
        new ProtoArraySnapshot(
            UInt64.valueOf(100), UInt64.valueOf(99), UInt64.ZERO, blockInformationList);

    final byte[] bytes = serializer.serialize(protoArraySnapshot);
    final ProtoArraySnapshot result = serializer.deserialize(bytes);
    assertThat(protoArraySnapshot).isEqualToComparingFieldByField(result);
  }

  @Test
  public void deserialize_shouldSupportOriginalFormat() {
    List<BlockInformation> blockInformationList = new ArrayList<>();
    BeaconBlock block1 = dataStructureUtil.randomBeaconBlock(10000);
    addBlockToBlockInformationList(blockInformationList, block1);

    ProtoArraySnapshot protoArraySnapshot =
        new ProtoArraySnapshot(
            UInt64.valueOf(100), UInt64.valueOf(99), UInt64.ZERO, blockInformationList);

    // Serialize to old format
    final byte[] oldSerializationFormat = serializeV1Format(protoArraySnapshot);
    final byte[] currentSerializationFormat = serializer.serialize(protoArraySnapshot);
    assertThat(oldSerializationFormat.length).isLessThan(currentSerializationFormat.length);

    final ProtoArraySnapshot deserialized = serializer.deserialize(oldSerializationFormat);
    assertThat(deserialized).isEqualToComparingFieldByField(protoArraySnapshot);
  }

  @Test
  public void deserialize_withNonZeroAnchorEpoch() {
    List<BlockInformation> blockInformationList = new ArrayList<>();
    BeaconBlock block1 = dataStructureUtil.randomBeaconBlock(10000);
    addBlockToBlockInformationList(blockInformationList, block1);

    final UInt64 anchorEpoch = UInt64.valueOf(123);
    ProtoArraySnapshot protoArraySnapshot =
        new ProtoArraySnapshot(
            UInt64.valueOf(100), UInt64.valueOf(99), anchorEpoch, blockInformationList);

    // Serialize
    final byte[] serialized = serializer.serialize(protoArraySnapshot);

    // Deserialize and check that value matches the original
    final ProtoArraySnapshot deserialized = serializer.deserialize(serialized);
    assertThat(deserialized.getInitialEpoch()).isEqualTo(anchorEpoch);
    assertThat(deserialized).isEqualToComparingFieldByField(protoArraySnapshot);
  }

  private void addBlockToBlockInformationList(List<BlockInformation> list, BeaconBlock block) {
    list.add(
        new BlockInformation(
            block.getSlot(),
            block.hash_tree_root(),
            block.getParentRoot(),
            block.getStateRoot(),
            UInt64.valueOf(101),
            UInt64.valueOf(100)));
  }

  private byte[] serializeV1Format(final ProtoArraySnapshot protoArraySnapshot) {
    // Serialize to original format without anchorEpoch saved
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeUInt64(protoArraySnapshot.getJustifiedEpoch().longValue());
              writer.writeUInt64(protoArraySnapshot.getFinalizedEpoch().longValue());
              writer.writeBytesList(
                  protoArraySnapshot.getBlockInformationList().stream()
                      .map(BlockInformation::toBytes)
                      .collect(toList()));
            });
    return bytes.toArrayUnsafe();
  }
}
