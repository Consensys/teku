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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfigDeneb.VERSIONED_HASH_VERSION_KZG;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodySchemaDeneb;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class MiscHelpersDeneb extends MiscHelpersCapella {
  private final Predicates predicates;
  private final BeaconBlockBodySchemaDeneb<?> beaconBlockBodySchema;

  public static MiscHelpersDeneb required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionDeneb()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Deneb misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  public MiscHelpersDeneb(
      final SpecConfigDeneb specConfig,
      final Predicates predicates,
      final SchemaDefinitionsDeneb schemaDefinitions) {
    super(specConfig);
    this.predicates = predicates;
    this.beaconBlockBodySchema =
        (BeaconBlockBodySchemaDeneb<?>) schemaDefinitions.getBeaconBlockBodySchema();
  }

  /**
   * Performs <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/polynomial-commitments.md#verify_blob_kzg_proof">verify_blob_kzg_proof</a>
   * on the given blob sidecar
   *
   * @param kzg the kzg implementation which will be used for verification
   * @param blobSidecar blob sidecar to verify
   * @return true if blob sidecar is valid
   */
  @Override
  public boolean verifyBlobKzgProof(final KZG kzg, final BlobSidecarOld blobSidecar) {
    return kzg.verifyBlobKzgProof(
        blobSidecar.getBlob().getBytes(),
        blobSidecar.getKZGCommitment(),
        blobSidecar.getKZGProof());
  }

  /**
   * Performs <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/polynomial-commitments.md#verify_blob_kzg_proof">verify_blob_kzg_proof</a>
   * on the given blob sidecar
   *
   * @param kzg the kzg implementation which will be used for verification
   * @param blobSidecar blob sidecar to verify
   * @return true if blob sidecar is valid
   */
  @Override
  public boolean verifyBlobKzgProof(final KZG kzg, final BlobSidecar blobSidecar) {
    return kzg.verifyBlobKzgProof(
        blobSidecar.getBlob().getBytes(),
        blobSidecar.getKZGCommitment(),
        blobSidecar.getKZGProof());
  }

  /**
   * Performs <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/polynomial-commitments.md#verify_blob_kzg_proof_batch">verify_blob_kzg_proof_batch</a>
   * on the given blob sidecars
   *
   * @param kzg the kzg implementation which will be used for verification
   * @param blobSidecars blob sidecars to verify, can be a partial set
   * @return true if all blob sidecars are valid
   */
  @Override
  public boolean verifyBlobKzgProofBatch(final KZG kzg, final List<BlobSidecar> blobSidecars) {
    final List<Bytes> blobs = new ArrayList<>();
    final List<KZGCommitment> kzgCommitments = new ArrayList<>();
    final List<KZGProof> kzgProofs = new ArrayList<>();

    blobSidecars.forEach(
        blobSidecar -> {
          blobs.add(blobSidecar.getBlob().getBytes());
          kzgCommitments.add(blobSidecar.getKZGCommitment());
          kzgProofs.add(blobSidecar.getKZGProof());
        });

    return kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs);
  }

  /**
   * Validates blob sidecars against block. We need to check block root and kzg commitment, it's
   * enough to guarantee BlobSidecars belong to block
   *
   * @param blobSidecars blob sidecars to validate
   * @param block block to validate blob sidecar against
   * @param kzgCommitmentsFromBlock kzg commitments from block. They could be extracted from block
   *     but since we already have them we can avoid extracting them again.
   */
  @Override
  public void validateBlobSidecarsBatchAgainstBlock(
      final List<BlobSidecar> blobSidecars,
      final BeaconBlock block,
      final List<KZGCommitment> kzgCommitmentsFromBlock) {

    final String slotAndBlockRoot = block.getSlotAndBlockRoot().toLogString();

    blobSidecars.forEach(
        blobSidecar -> {
          final UInt64 blobIndex = blobSidecar.getIndex();

          checkArgument(
              blobSidecar.getBlockRoot().equals(block.getRoot()),
              "Block and blob sidecar root mismatch for %s, blob index %s",
              slotAndBlockRoot,
              blobIndex);

          final KZGCommitment kzgCommitmentFromBlock;

          try {
            kzgCommitmentFromBlock = kzgCommitmentsFromBlock.get(blobIndex.intValue());
          } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(
                String.format(
                    "Blob sidecar index out of bound with respect to block %s, blob index %s",
                    slotAndBlockRoot, blobIndex));
          }

          checkArgument(
              blobSidecar.getKZGCommitment().equals(kzgCommitmentFromBlock),
              "Block and blob sidecar kzg commitments mismatch for %s, blob index %s",
              slotAndBlockRoot,
              blobIndex);
        });
  }

  /**
   * Verifies that blob sidecars are complete and with expected indexes
   *
   * @param completeVerifiedBlobSidecars blob sidecars to verify, It is assumed that it is an
   *     ordered list based on BlobSidecar index
   * @param kzgCommitmentsFromBlock kzg commitments from block.
   */
  @Override
  public void verifyBlobSidecarCompleteness(
      final List<BlobSidecar> completeVerifiedBlobSidecars,
      final List<KZGCommitment> kzgCommitmentsFromBlock)
      throws IllegalArgumentException {
    checkArgument(
        completeVerifiedBlobSidecars.size() == kzgCommitmentsFromBlock.size(),
        "Blob sidecars are not complete");

    IntStream.range(0, completeVerifiedBlobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = completeVerifiedBlobSidecars.get(index);
              final UInt64 blobIndex = blobSidecar.getIndex();

              checkArgument(
                  blobIndex.longValue() == index,
                  "Blob sidecar index mismatch, expected %s, got %s",
                  index,
                  blobIndex);
            });
  }

  /**
   * <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/beacon-chain.md#kzg_commitment_to_versioned_hash">kzg_commitment_to_versioned_hash</a>
   */
  @Override
  public VersionedHash kzgCommitmentToVersionedHash(final KZGCommitment kzgCommitment) {
    return VersionedHash.create(
        VERSIONED_HASH_VERSION_KZG, Hash.sha256(kzgCommitment.getBytesCompressed()));
  }

  @Override
  public UInt64 getMaxRequestBlocks() {
    return UInt64.valueOf(SpecConfigDeneb.required(specConfig).getMaxRequestBlocksDeneb());
  }

  @Override
  public Optional<MiscHelpersDeneb> toVersionDeneb() {
    return Optional.of(this);
  }

  public int getBlobKzgCommitmentsCount(final SignedBeaconBlock signedBeaconBlock) {
    return signedBeaconBlock
        .getMessage()
        .getBody()
        .toVersionDeneb()
        .map(BeaconBlockBodyDeneb::getBlobKzgCommitments)
        .map(SszList::size)
        .orElse(0);
  }

  public int getBlobSidecarKzgCommitmentGeneralizedIndex(final UInt64 blobSidecarIndex) {
    final long blobKzgCommitmentsGeneralizedIndex =
        beaconBlockBodySchema.getBlobKzgCommitmentsGeneralizedIndex();
    final long commitmentGeneralizedIndex =
        beaconBlockBodySchema
            .getBlobKzgCommitmentsSchema()
            .getChildGeneralizedIndex(blobSidecarIndex.longValue());
    return (int)
        GIndexUtil.gIdxCompose(blobKzgCommitmentsGeneralizedIndex, commitmentGeneralizedIndex);
  }

  public boolean verifyBlobSidecarMerkleProof(final BlobSidecar blobSidecar) {
    return predicates.isValidMerkleBranch(
        blobSidecar.getSszKZGCommitment().hashTreeRoot(),
        blobSidecar.getKzgCommitmentInclusionProof(),
        SpecConfigDeneb.required(specConfig).getKzgCommitmentInclusionProofDepth(),
        getBlobSidecarKzgCommitmentGeneralizedIndex(blobSidecar.getIndex()),
        blobSidecar.getBlockBodyRoot());
  }
}
