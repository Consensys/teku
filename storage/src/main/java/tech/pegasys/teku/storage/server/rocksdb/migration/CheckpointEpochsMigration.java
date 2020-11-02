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

package tech.pegasys.teku.storage.server.rocksdb.migration;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoArrayBuilder;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbHotDao;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbHotDao.HotUpdater;
import tech.pegasys.teku.storage.server.rocksdb.dataaccess.RocksDbProtoArrayDao;

public class CheckpointEpochsMigration {
  private static final Logger LOG = LogManager.getLogger();

  public static void migrate(final RocksDbHotDao hotDao, final RocksDbProtoArrayDao protoArrayDao) {
    final Optional<SignedBeaconBlock> hotBlock = hotDao.streamHotBlocks().findFirst();
    if (hotBlock
        .map(block -> hotDao.getHotBlockCheckpointEpochs(block.getRoot()).isPresent())
        .orElse(true)) {
      LOG.debug(
          "Checkpoint epochs migration not required. Either no hot blocks present or checkpoint epochs already populated.");
      return;
    }

    final Checkpoint finalizedCheckpoint = hotDao.getFinalizedCheckpoint().orElseThrow();
    final ProtoArray protoArray =
        new ProtoArrayBuilder()
            .protoArraySnapshot(protoArrayDao.getProtoArraySnapshot())
            .justifiedCheckpoint(hotDao.getJustifiedCheckpoint().orElseThrow())
            .finalizedCheckpoint(finalizedCheckpoint)
            .anchor(hotDao.getAnchor())
            .build();
    LOG.info("Migrating database to record fork choice information for blocks");
    try (final HotUpdater hotUpdater = hotDao.hotUpdater()) {
      protoArray
          .getNodes()
          .forEach(
              knownNode ->
                  hotUpdater.addHotBlockCheckpointEpochs(
                      knownNode.getBlockRoot(),
                      new CheckpointEpochs(
                          knownNode.getJustifiedEpoch(), knownNode.getFinalizedEpoch())));
      // Remove any hot blocks that are not in the protoarray snapshot. Sync will redownload them.
      try (final Stream<SignedBeaconBlock> blocks = hotDao.streamHotBlocks()) {
        blocks
            .map(SignedBeaconBlock::getRoot)
            .filter(blockRoot -> !protoArray.getIndices().containsKey(blockRoot))
            .peek(
                blockRoot ->
                    LOG.debug("Deleting block {} as it is not in protoarray snapshot", blockRoot))
            .forEach(hotUpdater::deleteHotBlock);
      }
    }
  }
}
