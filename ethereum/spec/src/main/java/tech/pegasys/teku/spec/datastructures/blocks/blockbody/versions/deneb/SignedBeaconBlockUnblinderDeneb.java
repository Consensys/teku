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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class SignedBeaconBlockUnblinderDeneb extends AbstractSignedBeaconBlockUnblinder {
  protected SafeFuture<ExecutionPayload> executionPayloadFuture;

  public SignedBeaconBlockUnblinderDeneb(
      final SchemaDefinitionsDeneb schemaDefinitions,
      final SignedBlindedBlockContainer signedBlindedBlockContainer) {
    super(schemaDefinitions, signedBlindedBlockContainer);
  }

  @Override
  public void setExecutionPayloadSupplier(
      final Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
    this.executionPayloadFuture = executionPayloadSupplier.get();
  }

  @Override
  public SafeFuture<SignedBeaconBlock> unblind() {

    final SignedBeaconBlock signedBlindedBeaconBlock = signedBlindedBlockContainer.getSignedBlock();

    if (!signedBlindedBeaconBlock.isBlinded()) {
      return SafeFuture.completedFuture(signedBlindedBeaconBlock);
    }

    checkNotNull(executionPayloadFuture, "executionPayload must be set");

    return executionPayloadFuture
        .thenApply(ExecutionPayloadDeneb::required)
        .thenApply(
            executionPayload -> {
              final BlindedBeaconBlockBodyDeneb blindedBody =
                  BlindedBeaconBlockBodyDeneb.required(
                      signedBlindedBeaconBlock.getMessage().getBody());
              checkState(
                  executionPayload
                      .hashTreeRoot()
                      .equals(blindedBody.getExecutionPayloadHeader().hashTreeRoot()),
                  "executionPayloadHeader root in blinded block do not match provided executionPayload root");
              return signedBlindedBeaconBlock.unblind(schemaDefinitions, executionPayload);
            });
  }
}
