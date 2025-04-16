/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.InclusionListSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.util.InclusionListUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class InclusionListFactory {

  private final ExecutionLayerChannel executionLayerChannel;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Spec spec;

  public InclusionListFactory(
      final ExecutionLayerChannel executionLayerChannel,
      final CombinedChainDataClient combinedChainDataClient,
      final Spec spec) {
    this.executionLayerChannel = executionLayerChannel;
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<Optional<InclusionList>> createInclusionList(
      final UInt64 slot, final UInt64 validatorIndex) {
    final InclusionListSchema inclusionListSchema =
        spec.atSlot(slot)
            .getSchemaDefinitions()
            .toVersionEip7805()
            .orElseThrow()
            .getInclusionListSchema();
    final InclusionListUtil inclusionListUtil =
        spec.atSlot(slot).getInclusionListUtil().orElseThrow();
    return combinedChainDataClient
        .getBestState()
        .orElseGet(
            () ->
                SafeFuture.failedFuture(
                    new IllegalStateException("Head state is not yet available")))
        .thenCompose(
            state -> {
              final Bytes32 committeeRoot =
                  inclusionListUtil.getInclusionListCommitteeRoot(state, slot);
              final Bytes32 parentHash =
                  BeaconStateElectra.required(state)
                      .getLatestExecutionPayloadHeader()
                      .getParentHash();
              return executionLayerChannel
                  .engineGetInclusionList(parentHash, slot)
                  .thenApply(
                      transactions ->
                          inclusionListSchema.create(
                              slot, validatorIndex, committeeRoot, transactions))
                  .thenApply(Optional::ofNullable);
            });
  }

    @SuppressWarnings("FutureReturnValueIgnored")
    public SafeFuture<Optional<List<Transaction>>> getInclusionList(
            final UInt64 slot) {
        final InclusionListSchema inclusionListSchema =
                spec.atSlot(slot)
                        .getSchemaDefinitions()
                        .toVersionEip7805()
                        .orElseThrow()
                        .getInclusionListSchema();
        final InclusionListUtil inclusionListUtil =
                spec.atSlot(slot).getInclusionListUtil().orElseThrow();
        return combinedChainDataClient
                .getBestState()
                .orElseGet(
                        () ->
                                SafeFuture.failedFuture(
                                        new IllegalStateException("Head state is not yet available")))
                .thenCompose(
                        state -> {
                            final Bytes32 parentHash =
                                    BeaconStateElectra.required(state)
                                            .getLatestExecutionPayloadHeader()
                                            .getParentHash();
                            return executionLayerChannel
                                    .engineGetInclusionList(parentHash, slot)
                                    .thenApply(Optional::ofNullable);
                        });
    }
}
