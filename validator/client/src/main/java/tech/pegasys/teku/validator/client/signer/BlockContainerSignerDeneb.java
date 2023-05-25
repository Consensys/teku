/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.validator.client.signer;

import java.util.Collections;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.validator.client.Validator;

public class BlockContainerSignerDeneb implements BlockContainerSigner {

  private final Spec spec;
  private final SchemaDefinitionsDeneb schemaDefinitions;

  public BlockContainerSignerDeneb(
      final Spec spec, final SchemaDefinitionsDeneb schemaDefinitions) {
    this.spec = spec;
    this.schemaDefinitions = schemaDefinitions;
  }

  @Override
  public SafeFuture<SignedBlockContainer> sign(
      final BlockContainer unsignedBlockContainer,
      final Validator validator,
      final ForkInfo forkInfo) {
    final BeaconBlock unsignedBlock = unsignedBlockContainer.getBlock();
    return unsignedBlockContainer
        .toBlinded()
        .map(
            // Blinded flow
            blindedBlockContainer -> {
              final List<BlindedBlobSidecar> blindedBlobSidecars =
                  blindedBlockContainer.getBlindedBlobSidecars().orElse(Collections.emptyList());
              return signBlock(unsignedBlock, validator, forkInfo)
                  .thenCombine(
                      signBlindedBlobSidecars(blindedBlobSidecars, validator, forkInfo),
                      this::createSignedBlindedBlockContents);
            })
        .orElseGet(
            // Unblinded flow
            () -> {
              final List<BlobSidecar> blobSidecars =
                  unsignedBlockContainer.getBlobSidecars().orElse(Collections.emptyList());
              return signBlock(unsignedBlock, validator, forkInfo)
                  .thenCombine(
                      signBlobSidecars(blobSidecars, validator, forkInfo),
                      this::createSignedBlockContents);
            });
  }

  private SignedBlockContainer createSignedBlockContents(
      final SignedBeaconBlock signedBlock, final List<SignedBlobSidecar> signedBlobSidecars) {
    return schemaDefinitions.getSignedBlockContentsSchema().create(signedBlock, signedBlobSidecars);
  }

  private SignedBlockContainer createSignedBlindedBlockContents(
      final SignedBeaconBlock signedBlock,
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    return schemaDefinitions
        .getSignedBlindedBlockContentsSchema()
        .create(signedBlock, signedBlindedBlobSidecars);
  }

  private SafeFuture<SignedBeaconBlock> signBlock(
      final BeaconBlock unsignedBlock, final Validator validator, final ForkInfo forkInfo) {
    return validator
        .getSigner()
        .signBlock(unsignedBlock, forkInfo)
        .thenApply(signature -> SignedBeaconBlock.create(spec, unsignedBlock, signature));
  }

  private SafeFuture<List<SignedBlobSidecar>> signBlobSidecars(
      final List<BlobSidecar> unsignedBlobSidecars,
      final Validator validator,
      final ForkInfo forkInfo) {
    return SafeFuture.collectAll(
        unsignedBlobSidecars.stream()
            .map(unsignedBlobSidecar -> signBlobSidecar(unsignedBlobSidecar, validator, forkInfo)));
  }

  private SafeFuture<SignedBlobSidecar> signBlobSidecar(
      final BlobSidecar unsignedBlobSidecar, final Validator validator, final ForkInfo forkInfo) {
    return validator
        .getSigner()
        .signBlobSidecar(unsignedBlobSidecar, forkInfo)
        .thenApply(
            signature ->
                schemaDefinitions
                    .getSignedBlobSidecarSchema()
                    .create(unsignedBlobSidecar, signature));
  }

  private SafeFuture<List<SignedBlindedBlobSidecar>> signBlindedBlobSidecars(
      final List<BlindedBlobSidecar> unsignedBlindedBlobSidecars,
      final Validator validator,
      final ForkInfo forkInfo) {
    return SafeFuture.collectAll(
        unsignedBlindedBlobSidecars.stream()
            .map(
                unsignedBlindedBlobSidecar ->
                    signBlindedBlobSidecar(unsignedBlindedBlobSidecar, validator, forkInfo)));
  }

  private SafeFuture<SignedBlindedBlobSidecar> signBlindedBlobSidecar(
      final BlindedBlobSidecar unsignedBlindedBlobSidecar,
      final Validator validator,
      final ForkInfo forkInfo) {
    return validator
        .getSigner()
        .signBlindedBlobSidecar(unsignedBlindedBlobSidecar, forkInfo)
        .thenApply(
            signature ->
                schemaDefinitions
                    .getSignedBlindedBlobSidecarSchema()
                    .create(unsignedBlindedBlobSidecar, signature));
  }
}
