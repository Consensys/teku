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

package tech.pegasys.teku.storage.server.rocksdb.schema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.protoarray.BlockInformation;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoArrayBuilder;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbHotDao;

public class ProtoArrayLoader {
  public static ProtoArray loadProtoArray(
      final RocksDbHotDao hotDao,
      final Optional<Checkpoint> anchor,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint) {

    // Build proto array
    final ProtoArray protoArray =
        new ProtoArrayBuilder()
            .anchor(anchor)
            .finalizedCheckpoint(finalizedCheckpoint)
            .justifiedCheckpoint(justifiedCheckpoint)
            .build();

    // First load all the block information
    final List<BlockInformation> blocksToLoad = new ArrayList<>();
    try (final Stream<SignedBeaconBlock> hotBlocks = hotDao.streamHotBlocks()) {
      hotBlocks.forEach(
          block -> {
            final CheckpointEpochs checkpointEpochs = loadCheckpointEpochs(hotDao, block);
            blocksToLoad.add(
                new BlockInformation(
                    block.getSlot(),
                    block.getRoot(),
                    block.getParent_root(),
                    block.getStateRoot(),
                    checkpointEpochs.getJustifiedEpoch(),
                    checkpointEpochs.getFinalizedEpoch()));
          });

      // Then add them to protoarray in slot order
      blocksToLoad.sort(Comparator.comparing(BlockInformation::getBlockSlot));
      blocksToLoad.forEach(
          blockInfo ->
              protoArray.onBlock(
                  blockInfo.getBlockSlot(),
                  blockInfo.getBlockRoot(),
                  blockInfo.getParentRoot(),
                  blockInfo.getStateRoot(),
                  blockInfo.getJustifiedEpoch(),
                  blockInfo.getFinalizedEpoch()));
    }
    return protoArray;
  }

  private static CheckpointEpochs loadCheckpointEpochs(
      final RocksDbHotDao hotDao, final SignedBeaconBlock block) {
    return hotDao
        .getHotBlockCheckpointEpochs(block.getRoot())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Checkpoint epochs not found for block " + block.getRoot()));
  }
}
