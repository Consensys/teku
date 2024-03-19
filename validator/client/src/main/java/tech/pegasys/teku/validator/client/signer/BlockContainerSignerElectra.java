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

package tech.pegasys.teku.validator.client.signer;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.electra.SignedBlockContentsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.InclusionListWithSignedSummary;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.SignedInclusionListSummary;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.validator.client.Validator;

public class BlockContainerSignerElectra extends BlockContainerSignerDeneb {

  public BlockContainerSignerElectra(Spec spec) {
    super(spec);
  }

  @Override
  public SafeFuture<SignedBlockContainer> sign(
      final BlockContainer unsignedBlockContainer,
      final Validator validator,
      final ForkInfo forkInfo) {
    return super.sign(unsignedBlockContainer, validator, forkInfo)
        .thenCompose(
            signedBlockContainer -> {
              final InclusionList inclusionList =
                  unsignedBlockContainer
                      .getInclusionList()
                      .orElseThrow(
                          () ->
                              new RuntimeException(
                                  String.format(
                                      "Unable to get inclusion list when signing Electra block at slot %s",
                                      unsignedBlockContainer.getSlot())));

              return signInclusionList(
                      unsignedBlockContainer.getSlot(), inclusionList, validator, forkInfo)
                  .thenApply(
                      signedInclusionList ->
                          getSignedBlockContentsSchema(unsignedBlockContainer.getSlot())
                              .create(
                                  signedBlockContainer.getSignedBlock(),
                                  signedBlockContainer.getKzgProofs().orElseThrow(),
                                  signedBlockContainer.getBlobs().orElseThrow(),
                                  signedInclusionList));
            });
  }

  private SafeFuture<SignedInclusionList> signInclusionList(
      final UInt64 slot,
      final InclusionList unsignedInclusionList,
      final Validator validator,
      final ForkInfo forkInfo) {
    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        SchemaDefinitionsElectra.required(spec.atSlot(slot).getSchemaDefinitions());

    return validator
        .getSigner()
        .signInclusionListSummary(slot, unsignedInclusionList.getInclusionListSummary(), forkInfo)
        .thenApply(
            blsSignature ->
                new SignedInclusionListSummary(
                    schemaDefinitionsElectra.getSignedInclusionListSummarySchema(),
                    unsignedInclusionList.getInclusionListSummary(),
                    new SszSignature(blsSignature)))
        .thenApply(
            signedInclusionListSummary ->
                new InclusionListWithSignedSummary(
                    schemaDefinitionsElectra.getInclusionListWithSignedSummarySchema(),
                    signedInclusionListSummary,
                    unsignedInclusionList.getTransactions()))
        .thenCompose(
            inclusionListWithSignedSummary ->
                validator
                    .getSigner()
                    .signInclusionListWithSignedSummary(
                        slot, inclusionListWithSignedSummary, forkInfo)
                    .thenApply(
                        blsSignature ->
                            new SignedInclusionList(
                                schemaDefinitionsElectra.getSignedInclusionListSchema(),
                                inclusionListWithSignedSummary.getSignedInclusionListSummary(),
                                inclusionListWithSignedSummary.getTransactions(),
                                new SszSignature(blsSignature))));
  }

  private SignedBlockContentsSchema getSignedBlockContentsSchema(final UInt64 slot) {
    return SchemaDefinitionsElectra.required(spec.atSlot(slot).getSchemaDefinitions())
        .getSignedBlockContentsSchema()
        .toVersionElectra()
        .orElseThrow();
  }
}
