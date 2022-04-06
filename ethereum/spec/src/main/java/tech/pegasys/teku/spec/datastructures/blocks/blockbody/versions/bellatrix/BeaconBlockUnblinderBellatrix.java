/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockUnblinderAltair;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class BeaconBlockUnblinderBellatrix extends BeaconBlockUnblinderAltair {
  protected SafeFuture<ExecutionPayload> executionPayloadFuture;

  public BeaconBlockUnblinderBellatrix(
      final SchemaDefinitions schemaDefinitions, final SignedBeaconBlock signedBlindedBeaconBlock) {
    super(schemaDefinitions, signedBlindedBeaconBlock);
  }

  @Override
  public BeaconBlockUnblinder executionPayload(
      Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
    this.executionPayloadFuture = executionPayloadSupplier.get();
    return this;
  }

  @Override
  public SafeFuture<SignedBeaconBlock> unblind() {
    BeaconBlock blindedBeaconBlock = signedBlindedBeaconBlock.getMessage();
    if (!blindedBeaconBlock.getBody().isBlinded()) {
      return SafeFuture.completedFuture(signedBlindedBeaconBlock);
    }

    checkNotNull(executionPayloadFuture, "executionPayload must be set");

    return executionPayloadFuture.thenApply(
        executionPayload -> {
          final BlindedBeaconBlockBodyBellatrix blindedBody =
              BlindedBeaconBlockBodyBellatrix.required(blindedBeaconBlock.getBody());

          checkState(
              executionPayload.hashTreeRoot().equals(blindedBody.hashTreeRoot()),
              "executionPayloadHeader root in blinded block do not match provided executionPayload root");

          final BeaconBlockBodyBellatrix unblindedBody =
              new BeaconBlockBodyBellatrixImpl(
                  (BeaconBlockBodySchemaBellatrixImpl) schemaDefinitions.getBeaconBlockBodySchema(),
                  new SszSignature(blindedBody.getRandaoReveal()),
                  blindedBody.getEth1Data(),
                  SszBytes32.of(blindedBody.getGraffiti()),
                  blindedBody.getProposerSlashings(),
                  blindedBody.getAttesterSlashings(),
                  blindedBody.getAttestations(),
                  blindedBody.getDeposits(),
                  blindedBody.getVoluntaryExits(),
                  blindedBody.getSyncAggregate(),
                  executionPayload);

          final BeaconBlock unblindedBlock =
              schemaDefinitions
                  .getBeaconBlockSchema()
                  .create(
                      blindedBeaconBlock.getSlot(),
                      blindedBeaconBlock.getProposerIndex(),
                      blindedBeaconBlock.getParentRoot(),
                      blindedBeaconBlock.getStateRoot(),
                      unblindedBody);

          return schemaDefinitions
              .getSignedBeaconBlockSchema()
              .create(unblindedBlock, signedBlindedBeaconBlock.getSignature());
        });
  }
}
