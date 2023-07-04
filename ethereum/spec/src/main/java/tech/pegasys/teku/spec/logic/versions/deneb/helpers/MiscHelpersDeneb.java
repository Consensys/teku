/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
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
      kzg = CKZG4844.createInstance(config.getFieldElementsPerBlob());
      kzg.loadTrustedSetup(config.getTrustedSetupPath().orElseThrow());
    } else {
      kzg = KZG.NOOP;
    }

    return kzg;
  }

  /**
   * <a
   * href="https://github.com/ethereum/consensus-specs/blob/dev/specs/deneb/fork-choice.md#is_data_available">is_data_available</a>
   */
  @Override
  public boolean isDataAvailable(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final List<KZGCommitment> kzgCommitments,
      final List<BlobSidecar> blobSidecars) {
    blobSidecars.forEach(
        blobSidecar -> {
          checkArgument(
              slot.equals(blobSidecar.getSlot()),
              "Blob sidecar slot %s does not match block slot %s",
              blobSidecar.getSlot(),
              slot);
          checkArgument(
              beaconBlockRoot.equals(blobSidecar.getBlockRoot()),
              "Blob sidecar block root %s does not match block root %s",
              blobSidecar.getBlockRoot(),
              beaconBlockRoot);
        });
    final List<Bytes> blobs =
        blobSidecars.stream()
            .map(BlobSidecar::getBlob)
            .map(Blob::getBytes)
            .collect(Collectors.toList());
    final List<KZGProof> proofs =
        blobSidecars.stream().map(BlobSidecar::getKZGProof).collect(Collectors.toList());

    return kzg.verifyBlobKzgProofBatch(blobs, kzgCommitments, proofs);
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
