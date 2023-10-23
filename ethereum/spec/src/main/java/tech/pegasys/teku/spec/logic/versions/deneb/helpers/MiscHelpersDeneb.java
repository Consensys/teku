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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.ckzg4844.CKZG4844;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.capella.helpers.MiscHelpersCapella;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;

public class MiscHelpersDeneb extends MiscHelpersCapella {

  private final KZG kzg;

  public MiscHelpersDeneb(final SpecConfigDeneb specConfig) {
    super(specConfig);
    this.kzg = initKZG(specConfig);
  }

  private static KZG initKZG(final SpecConfigDeneb config) {
    final KZG kzg;
    if (!config.getDenebForkEpoch().equals(SpecConfig.FAR_FUTURE_EPOCH) && !config.isKZGNoop()) {
      kzg = CKZG4844.getInstance();
      kzg.loadTrustedSetup(config.getTrustedSetupPath().orElseThrow());
    } else {
      kzg = KZG.NOOP;
    }

    return kzg;
  }

  public KZG getKzg() {
    return kzg;
  }

  /**
   * Performs complete data availability check <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/fork-choice.md#is_data_available">is_data_available</a>
   *
   * <p>In Deneb we don't need to retrieve data, everything is already available via blob sidecars.
   */
  @Override
  public boolean isDataAvailable(final List<BlobSidecar> blobSidecars, final BeaconBlock block) {

    final List<KZGCommitment> kzgCommitmentsFromBlock =
        BeaconBlockBodyDeneb.required(block.getBody()).getBlobKzgCommitments().stream()
            .map(SszKZGCommitment::getKZGCommitment)
            .toList();

    validateBlobSidecarsBatchAgainstBlock(blobSidecars, block, kzgCommitmentsFromBlock);

    if (!verifyBlobKzgProofBatch(blobSidecars)) {
      return false;
    }

    verifyBlobSidecarCompleteness(blobSidecars, kzgCommitmentsFromBlock);

    return true;
  }

  /**
   * Simplified version of {@link #isDataAvailable(List, BeaconBlock)} which accepts
   * blobs,commitments and proofs directly instead of blob sidecars
   */
  @Override
  public boolean isDataAvailable(
      final List<Bytes> blobs,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> proofs) {
    return kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, proofs);
  }

  /**
   * Performs a verifyBlobKzgProofBatch on the given blob sidecars
   *
   * @param blobSidecars blob sidecars to verify, can be a partial set
   * @return true if all blob sidecars are valid
   */
  @Override
  public boolean verifyBlobKzgProofBatch(final List<BlobSidecar> blobSidecars) {
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
   * Validates blob sidecars against block by matching all fields they have in common
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
              "Block and blob sidecar slot mismatch for %s, blob index %s",
              slotAndBlockRoot,
              blobIndex);

          checkArgument(
              blobSidecar.getSlot().equals(block.getSlot()),
              "Block and blob sidecar slot mismatch for %s, blob index %s",
              slotAndBlockRoot,
              blobIndex);

          checkArgument(
              blobSidecar.getProposerIndex().equals(block.getProposerIndex()),
              "Block and blob sidecar proposer index mismatch for %s, blob index %s",
              slotAndBlockRoot,
              blobIndex);
          checkArgument(
              blobSidecar.getBlockParentRoot().equals(block.getParentRoot()),
              "Block and blob sidecar parent block mismatch for %s, blob index %s",
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

  public KZGCommitment blobToKzgCommitment(final Blob blob) {
    return kzg.blobToKzgCommitment(blob.getBytes());
  }

  public KZGProof computeBlobKzgProof(final Blob blob, final KZGCommitment kzgCommitment) {
    return kzg.computeBlobKzgProof(blob.getBytes(), kzgCommitment);
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
}
