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

package tech.pegasys.teku.validator.coordinator;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;

public class ExecutionPayloadHeaderFactory {

  private final Spec spec;
  private final ForkChoiceNotifier forkChoiceNotifier;
  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager;
  private final ExecutionPayloadAndBlobSidecarsRevealer executionPayloadAndBlobSidecarsRevealer;

  public ExecutionPayloadHeaderFactory(
      final Spec spec,
      final ForkChoiceNotifier forkChoiceNotifier,
      final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager,
      final ExecutionPayloadAndBlobSidecarsRevealer executionPayloadAndBlobSidecarsRevealer) {
    this.spec = spec;
    this.forkChoiceNotifier = forkChoiceNotifier;
    this.executionLayerBlockProductionManager = executionLayerBlockProductionManager;
    this.executionPayloadAndBlobSidecarsRevealer = executionPayloadAndBlobSidecarsRevealer;
  }

  public SafeFuture<ExecutionPayloadHeader> createUnsignedHeader(
      final BeaconState slotState,
      final UInt64 slot,
      final Bytes32 parentRoot,
      final BLSPublicKey builderPublicKey) {
    final Optional<Integer> maybeBuilderIndex =
        BeaconStateCache.getTransitionCaches(slotState)
            .getValidatorIndexCache()
            .getValidatorIndex(slotState, builderPublicKey);
    if (maybeBuilderIndex.isEmpty()) {
      return SafeFuture.failedFuture(
          new IllegalArgumentException(
              "There is no validator index assigned to a builder with a public key "
                  + builderPublicKey));
    }
    final UInt64 builderIndex = UInt64.valueOf(maybeBuilderIndex.get());
    return forkChoiceNotifier
        .getPayloadId(parentRoot, slot)
        .thenCompose(
            maybeExecutionPayloadContext -> {
              final ExecutionPayloadContext executionPayloadContext =
                  maybeExecutionPayloadContext.orElseThrow();
              // TODO: EIP-7732 Disable the builder flow for the prototype
              final ExecutionPayloadResult executionPayloadResult =
                  executionLayerBlockProductionManager.initiateBlockProduction(
                      executionPayloadContext,
                      slotState,
                      false,
                      Optional.empty(),
                      BlockProductionPerformance.NOOP);
              return executionPayloadResult
                  .getLocalPayloadResponseRequired()
                  .thenApply(
                      getPayloadResponse -> {
                        final ExecutionPayloadHeader localBid =
                            createLocalBid(
                                slot,
                                builderIndex,
                                parentRoot,
                                executionPayloadContext,
                                getPayloadResponse);
                        executionPayloadAndBlobSidecarsRevealer.commit(
                            localBid, getPayloadResponse);
                        return localBid;
                      });
            });
  }

  private ExecutionPayloadHeader createLocalBid(
      final UInt64 slot,
      final UInt64 builderIndex,
      final Bytes32 parentRoot,
      final ExecutionPayloadContext executionPayloadContext,
      final GetPayloadResponse getPayloadResponse) {
    final SchemaDefinitionsEip7732 schemaDefinitions =
        SchemaDefinitionsEip7732.required(spec.atSlot(slot).getSchemaDefinitions());
    final SszList<SszKZGCommitment> blobKzgCommitments =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .createFromBlobsBundle(getPayloadResponse.getBlobsBundle().orElseThrow());
    return schemaDefinitions
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            builder ->
                builder
                    .parentBlockHash(executionPayloadContext::getParentHash)
                    .parentBlockRoot(() -> parentRoot)
                    .blockHash(getPayloadResponse.getExecutionPayload().getBlockHash())
                    .gasLimit(getPayloadResponse.getExecutionPayload().getGasLimit())
                    .builderIndex(() -> builderIndex)
                    .slot(() -> slot)
                    .value(
                        () ->
                            UInt64.valueOf(getPayloadResponse.getExecutionPayloadValue().toLong()))
                    .blobKzgCommitmentsRoot(blobKzgCommitments::hashTreeRoot));
  }
}
