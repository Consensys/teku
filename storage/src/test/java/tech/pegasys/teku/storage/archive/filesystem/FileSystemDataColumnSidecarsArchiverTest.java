/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.storage.archive.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@Disabled
public class FileSystemDataColumnSidecarsArchiverTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

  static Path testTempDir;
  static FileSystemDataColumnSidecarsArchiver archiver;

  @BeforeAll
  static void beforeEach() throws IOException {
    testTempDir = Files.createTempDirectory("columns");
    archiver = new FileSystemDataColumnSidecarsArchiver(SPEC, testTempDir);
  }

  @AfterEach
  public void tearDown() throws IOException {
    // Delete the temporary directory after each test
    if (Files.exists(testTempDir)) {
      try (Stream<Path> walk = Files.walk(testTempDir)) {
        walk.sorted((a, b) -> b.compareTo(a)) // Delete files before directories
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    // Ignore deletion errors in tests
                  }
                });
      }
    }
    // Recreate the directory for the next test
    Files.createDirectories(testTempDir);
  }

  @Test
  void testResolveArchivePath() {
    final UInt64 slot = UInt64.valueOf(1000);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 columnIndex = UInt64.valueOf(5);
    final DataColumnSlotAndIdentifier identifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex);

    final Path path = archiver.resolveArchivePath(identifier);

    // Verify the path structure: columns/<epoch>/<slot>_<blockRoot>_<columnIndex>.ssz
    final UInt64 epoch = SPEC.computeEpochAtSlot(slot);
    final String expectedFilename =
        slot + "_" + blockRoot.toUnprefixedHexString() + "_" + columnIndex + ".ssz";

    assertThat(path.getParent()).hasFileName(epoch.toString());
    assertThat(path).hasFileName(expectedFilename);
    assertTrue(path.toString().startsWith(testTempDir.toString()));
  }

  @Test
  void testResolveEpochDirectory() {
    final UInt64 epoch = UInt64.valueOf(42);
    final Path epochDir = archiver.resolveEpochDirectory(epoch);

    assertThat(epochDir).hasFileName(epoch.toString());
    assertThat(epochDir.getParent()).isEqualTo(testTempDir);
  }

  @Test
  void testArchiveAndRetrieveSingleSidecar() {
    final DataColumnSidecar sidecar = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSlotAndIdentifier identifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    // Archive the sidecar
    archiver.archive(identifier, sidecar);

    // Verify the file was created in the correct location
    final Path expectedPath = archiver.resolveArchivePath(identifier);
    assertThat(expectedPath).exists();

    // Retrieve the sidecar
    final Optional<DataColumnSidecar> retrieved = archiver.retrieve(identifier);

    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isEqualTo(sidecar);
  }

  @Test
  void testRetrieveNonExistentSidecar() {
    final UInt64 slot = UInt64.valueOf(999);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final DataColumnSlotAndIdentifier identifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ZERO);

    final Optional<DataColumnSidecar> retrieved = archiver.retrieve(identifier);

    assertThat(retrieved).isEmpty();
  }

  @Test
  void testRetrieveForSlot() {
    final UInt64 slot = UInt64.valueOf(200);
    //    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    // Create and archive multiple sidecars for the same slot and block
    final DataColumnSidecar sidecar1 = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecar2 = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecar3 = dataStructureUtil.randomDataColumnSidecar();

    final DataColumnSlotAndIdentifier identifier1 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar1);
    final DataColumnSlotAndIdentifier identifier2 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar2);
    final DataColumnSlotAndIdentifier identifier3 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar3);

    archiver.archive(identifier1, sidecar1);
    archiver.archive(identifier2, sidecar2);
    archiver.archive(identifier3, sidecar3);

    // Retrieve all sidecars for the slot
    final List<DataColumnSidecar> retrieved = archiver.retrieveForSlot(slot);

    assertThat(retrieved).hasSize(3);
    assertThat(retrieved).containsExactlyInAnyOrder(sidecar1, sidecar2, sidecar3);
  }

  @Test
  void testRetrieveForSlotReturnsEmptyForNonExistentEpoch() {
    final UInt64 slot = UInt64.valueOf(99999);

    final List<DataColumnSidecar> retrieved = archiver.retrieveForSlot(slot);

    assertThat(retrieved).isEmpty();
  }

  @Test
  void testRetrieveForBlock() {
    final UInt64 slot = UInt64.valueOf(300);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(slot, blockRoot);

    // Create multiple sidecars for the same block
    final DataColumnSidecar sidecar1 = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecar2 = dataStructureUtil.randomDataColumnSidecar();

    // Manually set the block root to be the same
    final DataColumnSlotAndIdentifier identifier1 =
        new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ZERO);
    final DataColumnSlotAndIdentifier identifier2 =
        new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ONE);

    archiver.archive(identifier1, sidecar1);
    archiver.archive(identifier2, sidecar2);

    // Retrieve all sidecars for the block
    final List<DataColumnSidecar> retrieved = archiver.retrieveForBlock(slotAndBlockRoot);

    assertThat(retrieved).hasSize(2);
  }

  @Test
  void testRetrieveForBlockFiltersCorrectly() {
    final UInt64 slot = UInt64.valueOf(400);
    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();

    // Create sidecars for different blocks in the same slot
    final DataColumnSidecar sidecar1 = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecar2 = dataStructureUtil.randomDataColumnSidecar();

    final DataColumnSlotAndIdentifier identifier1 =
        new DataColumnSlotAndIdentifier(slot, blockRoot1, UInt64.ZERO);
    final DataColumnSlotAndIdentifier identifier2 =
        new DataColumnSlotAndIdentifier(slot, blockRoot2, UInt64.ZERO);

    archiver.archive(identifier1, sidecar1);
    archiver.archive(identifier2, sidecar2);

    // Retrieve sidecars only for blockRoot1
    final List<DataColumnSidecar> retrieved =
        archiver.retrieveForBlock(new SlotAndBlockRoot(slot, blockRoot1));

    assertThat(retrieved).hasSize(1);
  }

  @Test
  void testPruneEpoch() {
    final UInt64 epoch = UInt64.valueOf(10);
    //    final UInt64 slot = SPEC.computeStartSlotAtEpoch(epoch);

    // Create and archive sidecars in the epoch
    final DataColumnSidecar sidecar1 = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecar2 = dataStructureUtil.randomDataColumnSidecar();

    final DataColumnSlotAndIdentifier identifier1 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar1);
    final DataColumnSlotAndIdentifier identifier2 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar2);

    archiver.archive(identifier1, sidecar1);
    archiver.archive(identifier2, sidecar2);

    // Verify sidecars exist
    assertThat(archiver.retrieve(identifier1)).isPresent();
    assertThat(archiver.retrieve(identifier2)).isPresent();

    // Prune the epoch
    archiver.pruneEpoch(epoch);

    // Verify epoch directory was deleted
    final Path epochDir = archiver.resolveEpochDirectory(epoch);
    assertThat(epochDir).doesNotExist();

    // Verify sidecars no longer exist
    assertThat(archiver.retrieve(identifier1)).isEmpty();
    assertThat(archiver.retrieve(identifier2)).isEmpty();
  }

  @Test
  void testPruneNonExistentEpoch() {
    final UInt64 epoch = UInt64.valueOf(999);

    // Pruning a non-existent epoch should not throw an exception
    archiver.pruneEpoch(epoch);
  }

  @Test
  void testArchiveSameIdentifierTwice() {
    //    final UInt64 slot = UInt64.valueOf(500);
    final DataColumnSidecar sidecar = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSlotAndIdentifier identifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    // Archive the same sidecar twice
    archiver.archive(identifier, sidecar);
    archiver.archive(identifier, sidecar);

    // Should still retrieve successfully
    final Optional<DataColumnSidecar> retrieved = archiver.retrieve(identifier);
    assertThat(retrieved).isPresent();
  }

  @Test
  void testMultipleSidecarsAcrossEpochs() {
    final UInt64 epoch1 = UInt64.ZERO;
    //    final UInt64 epoch2 = UInt64.ONE;
    //    final UInt64 slot1 = SPEC.computeStartSlotAtEpoch(epoch1);
    //    final UInt64 slot2 = SPEC.computeStartSlotAtEpoch(epoch2);

    final DataColumnSidecar sidecar1 = dataStructureUtil.randomDataColumnSidecar();
    final DataColumnSidecar sidecar2 = dataStructureUtil.randomDataColumnSidecar();

    final DataColumnSlotAndIdentifier identifier1 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar1);
    final DataColumnSlotAndIdentifier identifier2 =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar2);

    archiver.archive(identifier1, sidecar1);
    archiver.archive(identifier2, sidecar2);

    // Verify both exist
    assertThat(archiver.retrieve(identifier1)).isPresent();
    assertThat(archiver.retrieve(identifier2)).isPresent();

    // Prune only epoch1
    archiver.pruneEpoch(epoch1);

    // Verify epoch1 sidecar is gone but epoch2 remains
    assertThat(archiver.retrieve(identifier1)).isEmpty();
    assertThat(archiver.retrieve(identifier2)).isPresent();
  }
}
