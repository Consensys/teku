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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer.serialize;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class SSZTransitionRecorder implements TransitionRecorder {

  private static final ALogger STDOUT = new ALogger("stdout");
  private final Path outputDirectory;

  public SSZTransitionRecorder(final Path outputDirectory) {
    this.outputDirectory = mkdirs(outputDirectory);
  }

  @Override
  public void recordPreState(final UnsignedLong slot, final BeaconState state) {
    store(slotDirectory(slot).resolve("pre.ssz"), state);
  }

  @Override
  public void recordPostState(
      final UnsignedLong slot, final BeaconBlock processedBlock, final BeaconState state) {
    final Path slotDirectory = slotDirectory(slot);
    store(slotDirectory.resolve("block.ssz"), processedBlock);
    store(slotDirectory.resolve("post.ssz"), state);
  }

  private void store(final Path file, SimpleOffsetSerializable data) {
    try {
      Files.write(file, serialize(data).toArrayUnsafe());
    } catch (final IOException e) {
      STDOUT.log(Level.ERROR, "Failed to record data to " + file + ": " + e.getMessage());
    }
  }

  private Path mkdirs(final Path dir) {
    if (!dir.toFile().mkdirs() && !dir.toFile().isDirectory()) {
      STDOUT.log(
          Level.ERROR, "Failed to create transition record directory " + dir.toAbsolutePath());
    }
    return dir;
  }

  private Path slotDirectory(final UnsignedLong slot) {
    return mkdirs(outputDirectory.resolve(slot.toString()));
  }
}
