/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.generator;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil.ExecutionPayloadProposalData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.signatures.Signer;

public class ExecutionPayloadProposalTestUtil {

  private final Spec spec;

  public ExecutionPayloadProposalTestUtil(final Spec spec) {
    this.spec = spec;
  }

  public SafeFuture<SignedExecutionPayloadAndState> createExecutionPayload(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconBlockAndState blockAndState,
      final ExecutionPayloadProposalData executionPayloadProposalData,
      final boolean skipStateTransition) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(newSlot).getSchemaDefinitions());
    if (skipStateTransition) {
      final ExecutionPayloadEnvelope executionPayload =
          schemaDefinitions
              .getExecutionPayloadEnvelopeSchema()
              .create(
                  executionPayloadProposalData.executionPayload(),
                  executionPayloadProposalData.executionRequests(),
                  BUILDER_INDEX_SELF_BUILD,
                  blockAndState.getRoot(),
                  newSlot,
                  blockAndState.getState().hashTreeRoot());
      // Sign execution payload and set signature
      return signer
          .signExecutionPayloadEnvelope(executionPayload, blockAndState.getState().getForkInfo())
          .thenApply(
              signature -> {
                final SignedExecutionPayloadEnvelope signedExecutionPayload =
                    schemaDefinitions
                        .getSignedExecutionPayloadEnvelopeSchema()
                        .create(executionPayload, signature);
                return new SignedExecutionPayloadAndState(
                    signedExecutionPayload, blockAndState.getState());
              });
    }
    return spec.createNewUnsignedExecutionPayload(
            newSlot,
            BUILDER_INDEX_SELF_BUILD,
            blockAndState,
            SafeFuture.completedFuture(executionPayloadProposalData))
        .thenCompose(
            executionPayloadAndState -> {
              // Sign execution payload and set signature
              return signer
                  .signExecutionPayloadEnvelope(
                      executionPayloadAndState.executionPayload(),
                      executionPayloadAndState.state().getForkInfo())
                  .thenApply(
                      signature -> {
                        final SignedExecutionPayloadEnvelope signedExecutionPayload =
                            schemaDefinitions
                                .getSignedExecutionPayloadEnvelopeSchema()
                                .create(executionPayloadAndState.executionPayload(), signature);
                        return new SignedExecutionPayloadAndState(
                            signedExecutionPayload, executionPayloadAndState.state());
                      });
            });
  }
}
