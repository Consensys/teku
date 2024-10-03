/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.storage.archive.fsarchive;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.archive.DataArchive;
import tech.pegasys.teku.storage.archive.DataArchiveWriter;

public class FileSystemArchiveTest {

  private static final VersionedHash VERSIONED_HASH =
      VersionedHash.create(
          VERSIONED_HASH_VERSION_KZG,
          Bytes32.fromHexString(
              "0x391610cf24e7c540192b80ddcfea77b0d3912d94e922682f3b286eee041e6f76"));

  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final Predicates predicates = new Predicates(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
      SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersDeneb miscHelpersDeneb =
      new MiscHelpersDeneb(
          spec.getGenesisSpecConfig().toVersionDeneb().orElseThrow(),
          predicates,
          schemaDefinitionsDeneb);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  static DataArchive dataArchive;

  @BeforeAll
  static void beforeEach() throws IOException {
    Path temp = Files.createTempDirectory("blobs");
    dataArchive = new FileSystemArchive(temp);
  }

  BlobSidecar createBlobSidecar() {
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(1);
    final Blob blob = dataStructureUtil.randomBlob();
    final SszKZGProof proof = dataStructureUtil.randomSszKZGProof();

    return miscHelpersDeneb.constructBlobSidecar(signedBeaconBlock, UInt64.ZERO, blob, proof);
  }

  @Test
  void testWriteBlobSidecar() throws IOException {
    DataArchiveWriter<BlobSidecar> blobWriter = dataArchive.getBlobSidecarWriter();
    assertTrue(blobWriter.archive(createBlobSidecar()));
  }
}
