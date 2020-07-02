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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
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
            UnsignedLong.valueOf(100), UnsignedLong.valueOf(99), blockInformationList);

    final byte[] bytes = serializer.serialize(protoArraySnapshot);
    final ProtoArraySnapshot result = serializer.deserialize(bytes);
    assertThat(protoArraySnapshot).isEqualToComparingFieldByField(result);
  }

  private void addBlockToBlockInformationList(List<BlockInformation> list, BeaconBlock block) {
    list.add(
        new BlockInformation(
            block.getSlot(),
            block.hash_tree_root(),
            block.getParent_root(),
            block.getState_root(),
            UnsignedLong.valueOf(101),
            UnsignedLong.valueOf(100)));
  }
}
