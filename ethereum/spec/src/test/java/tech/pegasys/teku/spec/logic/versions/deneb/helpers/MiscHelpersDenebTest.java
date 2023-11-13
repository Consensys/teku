/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MiscHelpersDenebTest {

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

  @Test
  public void versionedHash() {
    final VersionedHash actual =
        miscHelpersDeneb.kzgCommitmentToVersionedHash(
            KZGCommitment.fromHexString(
                "0x85d1edf1ee88f68260e750abb2c766398ad1125d4e94e1de04034075ccbd2bb79c5689b952ef15374fd03ca2b2475371"));
    assertThat(actual).isEqualTo(VERSIONED_HASH);
  }

  @Test
  void validateBlobSidecarsAgainstBlock_shouldNotThrowOnValidBlobSidecar() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final List<BlobSidecarOld> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);

    // make sure we are testing something
    assertThat(blobSidecars).isNotEmpty();

    miscHelpersDeneb.validateBlobSidecarsBatchAgainstBlock(
        blobSidecars,
        block.getMessage(),
        BeaconBlockBodyDeneb.required(block.getMessage().getBody()).getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList());
  }

  @ParameterizedTest
  @EnumSource(value = BlobSidecarsAlteration.class)
  void validateBlobSidecarsAgainstBlock_shouldThrowOnBlobSidecarNotMatching(
      final BlobSidecarsAlteration blobSidecarsAlteration) {

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    final List<KZGCommitment> kzgCommitments =
        BeaconBlockBodyDeneb.required(block.getMessage().getBody()).getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList();

    // make sure we are testing something
    assertThat(kzgCommitments).isNotEmpty();

    final int indexToBeAltered =
        Math.toIntExact(dataStructureUtil.randomPositiveLong(kzgCommitments.size()));

    // let's create blobs with only one altered with the given alteration
    final List<BlobSidecarOld> blobSidecars =
        dataStructureUtil.randomBlobSidecarsForBlock(
            block,
            (index, randomBlobSidecarBuilder) -> {
              if (!index.equals(indexToBeAltered)) {
                return randomBlobSidecarBuilder;
              }

              return switch (blobSidecarsAlteration) {
                case BLOB_SIDECAR_PROPOSER_INDEX -> randomBlobSidecarBuilder.proposerIndex(
                    block.getProposerIndex().plus(1));
                case BLOB_SIDECAR_INDEX -> randomBlobSidecarBuilder.index(
                    UInt64.valueOf(kzgCommitments.size())); // out of bounds
                case BLOB_SIDECAR_PARENT_ROOT -> randomBlobSidecarBuilder.blockParentRoot(
                    block.getParentRoot().not());
                case BLOB_SIDECAR_KZG_COMMITMENT -> randomBlobSidecarBuilder.kzgCommitment(
                    BeaconBlockBodyDeneb.required(block.getMessage().getBody())
                        .getBlobKzgCommitments()
                        .get(index)
                        .getBytes()
                        .not());
              };
            });

    assertThatThrownBy(
            () ->
                miscHelpersDeneb.validateBlobSidecarsBatchAgainstBlock(
                    blobSidecars,
                    block.getMessage(),
                    BeaconBlockBodyDeneb.required(block.getMessage().getBody())
                        .getBlobKzgCommitments()
                        .stream()
                        .map(SszKZGCommitment::getKZGCommitment)
                        .toList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            switch (blobSidecarsAlteration) {
              case BLOB_SIDECAR_PROPOSER_INDEX -> "Block and blob sidecar proposer index mismatch";
              case BLOB_SIDECAR_INDEX -> "Blob sidecar index out of bound with respect to block";
              case BLOB_SIDECAR_PARENT_ROOT -> "Block and blob sidecar parent block mismatch";
              case BLOB_SIDECAR_KZG_COMMITMENT -> "Block and blob sidecar kzg commitments mismatch";
            })
        .hasMessageEndingWith(
            "blob index %s",
            blobSidecarsAlteration == BlobSidecarsAlteration.BLOB_SIDECAR_INDEX
                ? kzgCommitments.size() // out of bounds altered index
                : indexToBeAltered);
  }

  @Test
  void verifyBlobSidecarCompleteness_shouldThrowWhenSizesDoNotMatch() {
    assertThatThrownBy(
            () ->
                miscHelpersDeneb.verifyBlobSidecarCompleteness(
                    dataStructureUtil.randomBlobSidecars(1),
                    List.of(
                        dataStructureUtil.randomKZGCommitment(),
                        dataStructureUtil.randomKZGCommitment())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Blob sidecars are not complete");
  }

  @Test
  void verifyBlobSidecarCompleteness_shouldThrowWhenBlobSidecarIndexIsWrong() {
    final List<BlobSidecarOld> blobSidecars = dataStructureUtil.randomBlobSidecars(1);
    assertThatThrownBy(
            () ->
                miscHelpersDeneb.verifyBlobSidecarCompleteness(
                    blobSidecars, List.of(dataStructureUtil.randomKZGCommitment())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Blob sidecar index mismatch, expected 0, got %s", blobSidecars.get(0).getIndex());
  }

  private enum BlobSidecarsAlteration {
    BLOB_SIDECAR_PROPOSER_INDEX,
    BLOB_SIDECAR_INDEX,
    BLOB_SIDECAR_PARENT_ROOT,
    BLOB_SIDECAR_KZG_COMMITMENT,
  }

  @Test
  void verifyBlobSidecarMerkleProofShouldValidate() {
    final int numberOfCommitments = 4;
    final BeaconBlockBodyDeneb beaconBlockBody =
        BeaconBlockBodyDeneb.required(
            dataStructureUtil.randomBeaconBlockBodyWithCommitments(numberOfCommitments));
    assumeThat(beaconBlockBody.getBlobKzgCommitments().size()).isEqualTo(numberOfCommitments);
    final BeaconBlock beaconBlock =
        new BeaconBlock(
            schemaDefinitionsDeneb.getBeaconBlockSchema(),
            dataStructureUtil.randomSlot(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            beaconBlockBody);
    final BeaconBlockHeader blockHeader = BeaconBlockHeader.fromBlock(beaconBlock);

    // Let's check all blobSidecars
    for (int i = 0; i < numberOfCommitments; ++i) {
      final UInt64 blobSidecarIndex = UInt64.valueOf(i);
      final List<Bytes32> merkleProof =
          MerkleUtil.constructMerkleProof(
              beaconBlockBody.getBackingNode(),
              miscHelpersDeneb.getBlobSidecarKzgCommitmentGeneralizedIndex(blobSidecarIndex));
      assertThat(merkleProof.size())
          .isEqualTo(
              SpecConfigDeneb.required(spec.getGenesisSpecConfig())
                  .getKzgCommitmentInclusionProofDepth());

      final BlobSidecar blobSidecar =
          dataStructureUtil
              .createRandomBlobSidecarBuilder()
              .signedBeaconBlockHeader(
                  new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
              .index(blobSidecarIndex)
              .kzgCommitment(
                  beaconBlockBody
                      .getBlobKzgCommitments()
                      .get(blobSidecarIndex.intValue())
                      .getBytes())
              .kzgCommitmentInclusionProof(merkleProof)
              .build();
      assertThat(miscHelpersDeneb.verifyBlobSidecarMerkleProof(blobSidecar)).isTrue();

      // And the same blobSidecar but with wrong merkle proof
      for (int j = 0; j < numberOfCommitments; ++j) {
        if (j == i) {
          continue;
        }

        final UInt64 wrongIndex = UInt64.valueOf(j);
        final List<Bytes32> merkleProofWrong =
            MerkleUtil.constructMerkleProof(
                beaconBlockBody.getBackingNode(),
                miscHelpersDeneb.getBlobSidecarKzgCommitmentGeneralizedIndex(wrongIndex));
        assertThat(merkleProofWrong.size())
            .isEqualTo(
                SpecConfigDeneb.required(spec.getGenesisSpecConfig())
                    .getKzgCommitmentInclusionProofDepth());

        final BlobSidecar blobSidecarWrong =
            dataStructureUtil
                .createRandomBlobSidecarBuilder()
                .signedBeaconBlockHeader(
                    new SignedBeaconBlockHeader(blockHeader, dataStructureUtil.randomSignature()))
                .index(blobSidecarIndex)
                .kzgCommitment(
                    beaconBlockBody
                        .getBlobKzgCommitments()
                        .get(blobSidecarIndex.intValue())
                        .getBytes())
                .kzgCommitmentInclusionProof(merkleProofWrong)
                .build();
        assertThat(miscHelpersDeneb.verifyBlobSidecarMerkleProof(blobSidecarWrong)).isFalse();
      }
    }
  }
}
