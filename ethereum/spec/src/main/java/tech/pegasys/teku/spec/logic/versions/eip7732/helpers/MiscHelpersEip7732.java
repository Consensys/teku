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

package tech.pegasys.teku.spec.logic.versions.eip7732.helpers;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigEip7732;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.BeaconBlockBodySchemaEip7732;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class MiscHelpersEip7732 extends MiscHelpersElectra {
  private final SchemaDefinitionsEip7732 schemaDefinitions;

  public MiscHelpersEip7732(
      final SpecConfigEip7732 specConfig,
      final PredicatesEip7732 predicates,
      final SchemaDefinitionsEip7732 schemaDefinitions) {
    super(
        SpecConfigElectra.required(specConfig),
        predicates,
        SchemaDefinitionsElectra.required(schemaDefinitions));
    this.schemaDefinitions = schemaDefinitions;
  }

  public static MiscHelpersEip7732 required(final MiscHelpers miscHelpers) {
    return miscHelpers
        .toVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Eip7732 misc helpers but got: "
                        + miscHelpers.getClass().getSimpleName()));
  }

  public byte removeFlag(final byte participationFlags, final int flagIndex) {
    final byte flag = (byte) (1 << flagIndex);
    return (byte) (participationFlags & ~flag);
  }

  @Override
  public int getBlobSidecarKzgCommitmentGeneralizedIndex(final UInt64 blobSidecarIndex) {
    final long blobKzgCommitmentsRootGeneralizedIndex =
        BeaconBlockBodySchemaEip7732.required(beaconBlockBodySchema)
            .getBlobKzgCommitmentsRootGeneralizedIndex();
    final long commitmentGeneralizedIndex =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .getChildGeneralizedIndex(blobSidecarIndex.longValue());
    return (int)
        GIndexUtil.gIdxCompose(blobKzgCommitmentsRootGeneralizedIndex, commitmentGeneralizedIndex);
  }

  @Override
  public boolean verifyBlobSidecarMerkleProof(final BlobSidecar blobSidecar) {
    return predicates.isValidMerkleBranch(
        blobSidecar.getSszKZGCommitment().hashTreeRoot(),
        blobSidecar.getKzgCommitmentInclusionProof(),
        SpecConfigEip7732.required(specConfig).getKzgCommitmentInclusionProofDepthEip7732(),
        getBlobSidecarKzgCommitmentGeneralizedIndex(blobSidecar.getIndex()),
        blobSidecar.getBlockBodyRoot());
  }

  @Override
  public Optional<MiscHelpersEip7732> toVersionEip7732() {
    return Optional.of(this);
  }
}
