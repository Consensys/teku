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

package tech.pegasys.teku.storage.server.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class FileBasedDataColumnStorageTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

  private Path testTempDir;
  private FileBasedDataColumnStorage storage;

  @BeforeEach
  void setUp() throws IOException {
    testTempDir = Files.createTempDirectory("data-columns");
    storage = new FileBasedDataColumnStorage(SPEC, testTempDir);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (Files.exists(testTempDir)) {
      try (Stream<Path> walk = Files.walk(testTempDir)) {
        walk.sorted((a, b) -> b.compareTo(a))
            .forEach(
                path -> {
                  try {
                    Files.deleteIfExists(path);
                  } catch (IOException e) {
                    // Ignore
                  }
                });
      }
    }
  }

  private DataColumnSidecar createDataColumnSidecar(final UInt64 slot) {
    return dataStructureUtil.randomDataColumnSidecar(
        dataStructureUtil.randomSignedBeaconBlockHeader(slot), UInt64.ZERO);
  }

  @Test
  void testAddAndRetrieveSidecar() {
    final UInt64 slot = UInt64.valueOf(100);
    final DataColumnSidecar sidecar = createDataColumnSidecar(slot);
    final DataColumnSlotAndIdentifier identifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    // Add sidecar
    storage.addSidecar(sidecar);

    // Retrieve sidecar
    final Optional<DataColumnSidecar> retrieved = storage.getSidecar(identifier);
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isEqualTo(sidecar);
  }

  @Test
  void testAddAndRetrieveNonCanonicalSidecar() {
    final UInt64 slot = UInt64.valueOf(200);
    final DataColumnSidecar sidecar = createDataColumnSidecar(slot);
    final DataColumnSlotAndIdentifier identifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    // Add non-canonical sidecar
    storage.addNonCanonicalSidecar(sidecar);

    // Retrieve non-canonical sidecar
    final Optional<DataColumnSidecar> retrieved = storage.getNonCanonicalSidecar(identifier);
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isEqualTo(sidecar);
  }

  @Test
  void testRetrieveNonExistentSidecar() {
    final DataColumnSlotAndIdentifier identifier =
        new DataColumnSlotAndIdentifier(
            UInt64.valueOf(300), dataStructureUtil.randomBytes32(), UInt64.ZERO);

    final Optional<DataColumnSidecar> retrieved = storage.getSidecar(identifier);
    assertThat(retrieved).isEmpty();
  }

  @Test
  void testEpochDirectoryCreation() {
    final UInt64 slot = UInt64.valueOf(8); // Epoch 1 (8 slots per epoch)
    final DataColumnSidecar sidecar = createDataColumnSidecar(slot);

    storage.addSidecar(sidecar);

    final UInt64 epoch = SPEC.computeEpochAtSlot(slot);
    final Path epochDir = testTempDir.resolve(epoch.toString());
    assertThat(epochDir).exists();
  }

  @Test
  void testFilenameFormat() {
    final UInt64 slot = UInt64.valueOf(123);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 columnIndex = UInt64.valueOf(7);
    final DataColumnSlotAndIdentifier identifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex);

    final String filename = storage.formatFilename(identifier);
    assertThat(filename)
        .isEqualTo(
            String.format("%s_%s_%s.ssz", slot, blockRoot.toUnprefixedHexString(), columnIndex));
  }

  @Test
  void testParseFilename() {
    final UInt64 slot = UInt64.valueOf(456);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 columnIndex = UInt64.valueOf(3);

    final String filename =
        String.format("%s_%s_%s.ssz", slot, blockRoot.toUnprefixedHexString(), columnIndex);

    final Optional<DataColumnSlotAndIdentifier> parsed = storage.parseFilename(filename);
    assertThat(parsed).isPresent();
    assertThat(parsed.get().slot()).isEqualTo(slot);
    assertThat(parsed.get().blockRoot()).isEqualTo(blockRoot);
    assertThat(parsed.get().columnIndex()).isEqualTo(columnIndex);
  }

  @Test
  void testParseInvalidFilename() {
    assertThat(storage.parseFilename("invalid")).isEmpty();
    assertThat(storage.parseFilename("123_abc.ssz")).isEmpty();
    assertThat(storage.parseFilename("123_abc_def_ghi.ssz")).isEmpty();
  }

  @Test
  void testGetIdentifiersForSlot() {
    final UInt64 slot = UInt64.valueOf(64);
    final DataColumnSidecar sidecar1 = createDataColumnSidecar(slot);
    final DataColumnSidecar sidecar2 = createDataColumnSidecar(slot);

    storage.addSidecar(sidecar1);
    storage.addSidecar(sidecar2);

    final List<DataColumnSlotAndIdentifier> identifiers = storage.getIdentifiers(slot);
    assertThat(identifiers).hasSize(2);
    assertThat(identifiers).extracting(DataColumnSlotAndIdentifier::slot).containsOnly(slot);
  }

  @Test
  void testGetIdentifiersForEmptySlot() {
    final UInt64 slot = UInt64.valueOf(999);
    final List<DataColumnSlotAndIdentifier> identifiers = storage.getIdentifiers(slot);
    assertThat(identifiers).isEmpty();
  }

  @Test
  void testGetIdentifiersWithBlockRootFilter() {
    final UInt64 slot = UInt64.valueOf(64);
    final DataColumnSidecar sidecar1 = createDataColumnSidecar(slot);
    final DataColumnSidecar sidecar2 = createDataColumnSidecar(slot);

    storage.addSidecar(sidecar1);
    storage.addSidecar(sidecar2);

    final DataColumnSlotAndIdentifier id1 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar1);
    final DataColumnSlotAndIdentifier id2 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar2);

    // Filter by specific blockRoot should only return matching sidecars
    final List<DataColumnSlotAndIdentifier> identifiers1 =
        storage.getIdentifiers(slot, id1.blockRoot());
    assertThat(identifiers1).hasSize(1);
    assertThat(identifiers1.get(0).blockRoot()).isEqualTo(id1.blockRoot());

    final List<DataColumnSlotAndIdentifier> identifiers2 =
        storage.getIdentifiers(slot, id2.blockRoot());
    assertThat(identifiers2).hasSize(1);
    assertThat(identifiers2.get(0).blockRoot()).isEqualTo(id2.blockRoot());
  }

  @Test
  void testStreamIdentifiersInRange() {
    final UInt64 slot1 = UInt64.valueOf(8); // Epoch 1
    final UInt64 slot2 = UInt64.valueOf(16); // Epoch 2
    final UInt64 slot3 = UInt64.valueOf(24); // Epoch 3

    storage.addSidecar(createDataColumnSidecar(slot1));
    storage.addSidecar(createDataColumnSidecar(slot2));
    storage.addSidecar(createDataColumnSidecar(slot3));

    final List<DataColumnSlotAndIdentifier> identifiers =
        storage.streamIdentifiers(slot1, slot2).collect(Collectors.toList());

    assertThat(identifiers).hasSize(2);
    assertThat(identifiers)
        .extracting(DataColumnSlotAndIdentifier::slot)
        .containsExactlyInAnyOrder(slot1, slot2);
  }

  @Test
  void testPruneEpochs() {
    // Slots in epoch 0, 1, 2 (8 slots per epoch)
    final UInt64 slot0 = UInt64.valueOf(5); // Epoch 0 (slots 0-7)
    final UInt64 slot1 = UInt64.valueOf(10); // Epoch 1 (slots 8-15)
    final UInt64 slot2 = UInt64.valueOf(18); // Epoch 2 (slots 16-23)

    storage.addSidecar(createDataColumnSidecar(slot0));
    storage.addSidecar(createDataColumnSidecar(slot1));
    storage.addSidecar(createDataColumnSidecar(slot2));

    // Prune up to slot 13 (in epoch 1, should delete epochs 0 and 1, keep epoch 2)
    final UInt64 pruneUpToSlot = UInt64.valueOf(13);
    final Optional<UInt64> lastPrunedEpoch = storage.pruneEpochs(pruneUpToSlot, 10);

    assertThat(lastPrunedEpoch).isPresent();

    // Epoch 0 and 1 should be deleted
    final Path epoch0Dir = testTempDir.resolve("0");
    final Path epoch1Dir = testTempDir.resolve("1");
    final Path epoch2Dir = testTempDir.resolve("2");

    assertThat(epoch0Dir).doesNotExist();
    assertThat(epoch1Dir).doesNotExist();
    assertThat(epoch2Dir).exists();
  }

  @Test
  void testPruneEpochsWithLimit() {
    // Slots in epochs 0, 1, 2, 3 (8 slots per epoch)
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(5))); // Epoch 0
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(12))); // Epoch 1
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(20))); // Epoch 2
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(28))); // Epoch 3

    // Prune up to slot 28 (epoch 3), but limit to 2 epochs
    final Optional<UInt64> lastPrunedEpoch = storage.pruneEpochs(UInt64.valueOf(28), 2);

    assertThat(lastPrunedEpoch).isPresent();
    assertThat(lastPrunedEpoch.get()).isEqualTo(UInt64.ONE);

    // Only epochs 0 and 1 should be deleted (limit = 2)
    assertThat(testTempDir.resolve("0")).doesNotExist();
    assertThat(testTempDir.resolve("1")).doesNotExist();
    assertThat(testTempDir.resolve("2")).exists();
  }

  @Test
  void testEarliestAvailableSlot() {
    assertThat(storage.getEarliestAvailableSlot()).isEmpty();

    final UInt64 slot = UInt64.valueOf(12345);
    storage.setEarliestAvailableSlot(slot);

    assertThat(storage.getEarliestAvailableSlot()).hasValue(slot);
  }

  @Test
  void testFirstCustodyIncompleteSlot() {
    assertThat(storage.getFirstCustodyIncompleteSlot()).isEmpty();

    final UInt64 slot = UInt64.valueOf(54321);
    storage.setFirstCustodyIncompleteSlot(slot);

    assertThat(storage.getFirstCustodyIncompleteSlot()).hasValue(slot);
  }

  @Test
  void testGetEarliestSlotByScan() {
    // No files yet
    assertThat(storage.getEarliestSlot()).isEmpty();

    // Add sidecars at different slots
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(100)));
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(50)));
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(75)));

    // Should return slot 50
    assertThat(storage.getEarliestSlot()).hasValue(UInt64.valueOf(50));
  }

  @Test
  void testGetCount() {
    assertThat(storage.getCount()).isZero();

    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(8))); // Epoch 1
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(9))); // Epoch 1
    storage.addSidecar(createDataColumnSidecar(UInt64.valueOf(16))); // Epoch 2

    assertThat(storage.getCount()).isEqualTo(3);
  }

  @Test
  void testMigrationComplete() {
    assertThat(storage.isMigrationComplete()).isFalse();

    storage.setMigrationComplete(true);
    assertThat(storage.isMigrationComplete()).isTrue();

    // Create new storage instance with same directory
    final FileBasedDataColumnStorage newStorage = new FileBasedDataColumnStorage(SPEC, testTempDir);
    assertThat(newStorage.isMigrationComplete()).isTrue();
  }

  @Test
  void testSidecarPathResolution() {
    final UInt64 slot = UInt64.valueOf(8); // Epoch 1 (8 slots per epoch)
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 columnIndex = UInt64.valueOf(5);
    final DataColumnSlotAndIdentifier identifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex);

    final Path sidecarPath = storage.resolveSidecarPath(identifier);

    final UInt64 epoch = SPEC.computeEpochAtSlot(slot);
    final String expectedFilename =
        String.format("%s_%s_%s.ssz", slot, blockRoot.toUnprefixedHexString(), columnIndex);

    assertThat(sidecarPath.getParent()).hasFileName(epoch.toString());
    assertThat(sidecarPath).hasFileName(expectedFilename);
  }

  @Test
  void testBothCanonicalAndNonCanonicalInSameDirectory() {
    final UInt64 slot = UInt64.valueOf(64);
    final DataColumnSidecar canonical = createDataColumnSidecar(slot);
    final DataColumnSidecar nonCanonical = createDataColumnSidecar(slot);

    storage.addSidecar(canonical);
    storage.addNonCanonicalSidecar(nonCanonical);

    // Both should be in the same epoch directory
    final UInt64 epoch = SPEC.computeEpochAtSlot(slot);
    final Path epochDir = testTempDir.resolve(epoch.toString());

    assertThat(epochDir).exists();

    // Count files in epoch directory
    try (Stream<Path> files = Files.list(epochDir)) {
      assertThat(files.filter(p -> p.toString().endsWith(".ssz")).count()).isEqualTo(2);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testCorruptedFileReturnsEmpty() throws IOException {
    final UInt64 slot = UInt64.valueOf(100);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 columnIndex = UInt64.ZERO;
    final DataColumnSlotAndIdentifier identifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, columnIndex);

    // Create corrupted file
    final Path sidecarPath = storage.resolveSidecarPath(identifier);
    Files.createDirectories(sidecarPath.getParent());
    Files.writeString(sidecarPath, "corrupted data");

    // Should return empty due to deserialization failure
    final Optional<DataColumnSidecar> retrieved = storage.getSidecar(identifier);
    assertThat(retrieved).isEmpty();
  }
}
