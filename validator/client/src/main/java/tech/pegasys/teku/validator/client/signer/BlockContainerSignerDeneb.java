/*
 * Copyright Consensys Software Inc., 2023
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

import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
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
    return signBlock(unsignedBlock, validator, forkInfo)
        .thenApply(
            signedBlock -> {
              if (signedBlock.isBlinded()) {
                return signedBlock;
              } else {
                final List<KZGProof> kzgProofs =
                    unsignedBlockContainer
                        .getKzgProofs()
                        .map(
                            sszKzgProofs ->
                                sszKzgProofs.asList().stream()
                                    .map(SszKZGProof::getKZGProof)
                                    .toList())
                        .orElseThrow(
                            () ->
                                new RuntimeException(
                                    String.format(
                                        "Unable to get KZG Proofs when signing Deneb block at slot %d",
                                        unsignedBlockContainer.getSlot().longValue())));
                final List<Blob> blobs =
                    unsignedBlockContainer
                        .getBlobs()
                        .map(SszCollection::asList)
                        .orElseThrow(
                            () ->
                                new RuntimeException(
                                    String.format(
                                        "Unable to get blobs when signing Deneb block at slot %d",
                                        unsignedBlockContainer.getSlot().longValue())));
                return schemaDefinitions
                    .getSignedBlockContentsSchema()
                    .create(signedBlock, kzgProofs, blobs);
              }
            });
  }

  private SafeFuture<SignedBeaconBlock> signBlock(
      final BeaconBlock unsignedBlock, final Validator validator, final ForkInfo forkInfo) {
    return validator
        .getSigner()
        .signBlock(unsignedBlock, forkInfo)
        .thenApply(signature -> SignedBeaconBlock.create(spec, unsignedBlock, signature));
  }
}
