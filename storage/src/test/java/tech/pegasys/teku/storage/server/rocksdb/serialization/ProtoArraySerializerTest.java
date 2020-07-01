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

import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.assertThatProtoArrayMatches;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.protoarray.ProtoArray;

public class ProtoArraySerializerTest {
  private final ProtoArraySerializer serializer = new ProtoArraySerializer();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  public void shouldEncodeAndDecodeWithMultipleBlocks() {
    // init ProtoArray
    ProtoArray protoArray =
        new ProtoArray(
            10000,
            UnsignedLong.valueOf(100),
            UnsignedLong.valueOf(99),
            new ArrayList<>(),
            new HashMap<>());

    BeaconBlock block1 = dataStructureUtil.randomBeaconBlock(10000);
    BeaconBlock block2 = dataStructureUtil.randomBeaconBlock(10001);
    BeaconBlock block3 = dataStructureUtil.randomBeaconBlock(10002);
    addBlockToProtoArray(protoArray, block1);
    addBlockToProtoArray(protoArray, block2);
    addBlockToProtoArray(protoArray, block3);

    final byte[] bytes = serializer.serialize(protoArray);
    final ProtoArray result = serializer.deserialize(bytes);
    assertThatProtoArrayMatches(protoArray, result);
  }

  private void addBlockToProtoArray(ProtoArray protoArray, BeaconBlock block1) {
    protoArray.onBlock(
        block1.getSlot(),
        block1.hash_tree_root(),
        block1.getParent_root(),
        block1.getState_root(),
        UnsignedLong.valueOf(101),
        UnsignedLong.valueOf(100));
  }
}
