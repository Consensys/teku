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

package tech.pegasys.teku.data.recorder;

import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.serialize;

import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.data.BlockProcessingRecord;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class SSZTransitionRecorder {

  private static final Logger LOG = LogManager.getLogger();

  private final Path outputDirectory;

  public SSZTransitionRecorder(final Path outputDirectory) {
    this.outputDirectory = mkdirs(outputDirectory);
  }

  @Subscribe
  public void onGenesis(final AnchorPoint anchor) {
    if (!anchor.isGenesis()) {
      return;
    }
    final BeaconState genesisState = anchor.getState();
    store(outputDirectory.resolve("genesis.ssz"), genesisState);
  }

  @Subscribe
  public void onBlockProcessingRecord(final BlockProcessingRecord record) {
    final Path slotDirectory = slotDirectory(record.getBlock().getSlot());
    store(slotDirectory.resolve("block.ssz"), record.getBlock());
    store(slotDirectory.resolve("post.ssz"), record.getPostState());
  }

  private void store(final Path file, SimpleOffsetSerializable data) {
    try {
      Files.write(file, serialize(data).toArrayUnsafe());
    } catch (final IOException e) {
      LOG.error("Failed to record data to " + file, e);
    }
  }

  private Path mkdirs(final Path dir) {
    if (!dir.toFile().mkdirs() && !dir.toFile().isDirectory()) {
      LOG.error("Failed to create transition record directory {}", dir.toAbsolutePath());
    }
    return dir;
  }

  private Path slotDirectory(final UInt64 slot) {
    return mkdirs(outputDirectory.resolve(slot.toString()));
  }
}
