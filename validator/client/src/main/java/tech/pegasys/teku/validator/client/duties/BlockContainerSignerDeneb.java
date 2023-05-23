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

package tech.pegasys.teku.validator.client.duties;

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
      final Validator validator,
      final ForkInfo forkInfo,
      final BlockContainer unsignedBlockContainer) {
    final BeaconBlock unsignedBlock = unsignedBlockContainer.getBlock();
    final SafeFuture<SignedBeaconBlock> signedBlock =
        validator
            .getSigner()
            .signBlock(unsignedBlock, forkInfo)
            .thenApply(signature -> SignedBeaconBlock.create(spec, unsignedBlock, signature));
    return unsignedBlockContainer
        .toBlinded()
        .map(
            blindedBlockContainer -> {
              final List<BlindedBlobSidecar> blindedBlobSidecars =
                  blindedBlockContainer.getBlindedBlobSidecars().orElse(Collections.emptyList());
              return signedBlock.thenCombine(
                  signBlindedBlobSidecars(blindedBlobSidecars, validator, forkInfo),
                  (signedBeaconBlock, signedBlindedBlobSidecars) ->
                      schemaDefinitions
                          .getSignedBlindedBlockContentsSchema()
                          .create(signedBeaconBlock, signedBlindedBlobSidecars)
                          .toSignedBlockContainer());
            })
        .orElseGet(
            () -> {
              final List<BlobSidecar> blobSidecars =
                  unsignedBlockContainer.getBlobSidecars().orElse(Collections.emptyList());
              return signedBlock.thenCombine(
                  signBlobSidecars(blobSidecars, validator, forkInfo),
                  (signedBeaconBlock, signedBlobSidecars) ->
                      schemaDefinitions
                          .getSignedBlockContentsSchema()
                          .create(signedBeaconBlock, signedBlobSidecars));
            });
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
