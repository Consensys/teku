/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

class ForkChoicePayloadExecutorEip7732 implements OptimisticExecutionPayloadExecutor {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionLayerChannel executionLayer;
  private Optional<SafeFuture<PayloadValidationResult>> result = Optional.empty();

  ForkChoicePayloadExecutorEip7732(final ExecutionLayerChannel executionLayer) {
    this.executionLayer = executionLayer;
  }

  public static ForkChoicePayloadExecutorEip7732 create(
      final ExecutionLayerChannel executionLayer) {
    return new ForkChoicePayloadExecutorEip7732(executionLayer);
  }

  public SafeFuture<PayloadValidationResult> getExecutionResult() {
    return result.orElse(
        SafeFuture.completedFuture(new PayloadValidationResult(PayloadStatus.VALID)));
  }

  @Override
  public boolean optimisticallyExecute(
      final ExecutionPayloadHeader latestExecutionPayloadHeader,
      final NewPayloadRequest payloadToExecute) {
    final ExecutionPayload executionPayload = payloadToExecute.getExecutionPayload();
    if (executionPayload.isDefault()) {
      // We're still pre-merge so no payload to execute
      // Note that the BlockProcessor will have already failed if this is default and shouldn't be
      // because it checks the parentRoot matches
      return true;
    }

    result =
        Optional.of(
            executionLayer
                .engineNewPayload(payloadToExecute)
                .thenCompose(
                    result -> SafeFuture.completedFuture(new PayloadValidationResult(result)))
                .exceptionally(
                    error -> {
                      LOG.error("Error while validating payload", error);
                      return new PayloadValidationResult(PayloadStatus.failedExecution(error));
                    }));

    return true;
  }
}
