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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableListTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class FileSystemBlobSidecarsArchiverTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalDeneb();
  private final Predicates predicates = new Predicates(SPEC.getGenesisSpecConfig());
  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
      SchemaDefinitionsDeneb.required(SPEC.getGenesisSchemaDefinitions());
  private final MiscHelpersDeneb miscHelpersDeneb =
      new MiscHelpersDeneb(
          SPEC.getGenesisSpecConfig().toVersionDeneb().orElseThrow(),
          predicates,
          schemaDefinitionsDeneb);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);

  static Path testTempDir;
  static FileSystemBlobSidecarsArchiver blobSidecarsArchiver;

  @BeforeAll
  static void beforeEach() throws IOException {
    testTempDir = Files.createTempDirectory("blobs");
    blobSidecarsArchiver = new FileSystemBlobSidecarsArchiver(SPEC, testTempDir);
  }

  @AfterEach
  public void tearDown() throws IOException {
    // Delete the temporary directory after each test
    if (Files.exists(testTempDir)) {
      try (Stream<Path> walk = Files.walk(testTempDir)) {
        walk.map(Path::toFile)
            .forEach(
                file -> {
                  if (!file.delete()) {
                    file.deleteOnExit();
                  }
                });
      }
    }
  }

  @Test
  void testResolve() {
    final SlotAndBlockRootAndBlobIndex slotAndBlockRootAndBlobIndex =
        new SlotAndBlockRootAndBlobIndex(
            UInt64.ONE, dataStructureUtil.randomBytes32(), UInt64.ZERO);
    final Path path =
        blobSidecarsArchiver.resolve(slotAndBlockRootAndBlobIndex.getSlotAndBlockRoot());

    // Check if the file path is correct. Doesn't check the intermediate directories.
    assertTrue(path.toString().startsWith(testTempDir.toString()));
    assertTrue(
        path.toString()
            .endsWith(slotAndBlockRootAndBlobIndex.getBlockRoot().toUnprefixedHexString()));
  }

  @Test
  void testArchiveWithEmptyList() {
    assertTrue(blobSidecarsArchiver.archive(dataStructureUtil.randomSlotAndBlockRoot(), List.of()));
  }

  @Test
  void testArchiveWithNullList() {
    assertTrue(blobSidecarsArchiver.archive(dataStructureUtil.randomSlotAndBlockRoot(), null));
  }

  @Test
  void testWriteBlobSidecar() throws IOException {
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    final List<BlobSidecar> blobSidecars = List.of(createBlobSidecar());
    assertTrue(blobSidecarsArchiver.archive(slotAndBlockRoot, blobSidecars));

    // Check if the file was written
    try (final FileInputStream indexFile =
            new FileInputStream(
                testTempDir.resolve(FileSystemBlobSidecarsArchiver.INDEX_FILE).toFile());
        final FileInputStream blobSidecarsFile =
            new FileInputStream(blobSidecarsArchiver.resolve(slotAndBlockRoot).toFile())) {
      final String indexFileContent = new String(indexFile.readAllBytes(), StandardCharsets.UTF_8);
      final String expectedIndexFileContent =
          slotAndBlockRoot.getSlot().toString()
              + " "
              + slotAndBlockRoot.getBlockRoot().toUnprefixedHexString();
      // Windows new lines are different, so don't include new lines in the comparison.
      assertTrue(indexFileContent.contains(expectedIndexFileContent));

      final String blobSidecarsJson =
          new String(blobSidecarsFile.readAllBytes(), StandardCharsets.UTF_8);

      final DeserializableListTypeDefinition<BlobSidecar> blobSidecarsType =
          new DeserializableListTypeDefinition<>(
              schemaDefinitionsDeneb.getBlobSidecarSchema().getJsonTypeDefinition());

      assertThat(JsonUtil.parse(blobSidecarsJson, blobSidecarsType)).isEqualTo(blobSidecars);
    }
  }

  @Test
  void testFileAlreadyExists() {
    final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();
    final List<BlobSidecar> blobSidecars = List.of(createBlobSidecar());
    assertTrue(blobSidecarsArchiver.archive(slotAndBlockRoot, blobSidecars));
    // Try to write the same file again
    assertFalse(blobSidecarsArchiver.archive(slotAndBlockRoot, blobSidecars));
  }

  private BlobSidecar createBlobSidecar() {
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(1);
    final Blob blob = dataStructureUtil.randomBlob();
    final SszKZGProof proof = dataStructureUtil.randomSszKZGProof();
    return miscHelpersDeneb.constructBlobSidecar(signedBeaconBlock, UInt64.ZERO, blob, proof);
  }
}
