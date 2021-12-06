/*
 * Copyright 2021 ConsenSys AG.
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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.versions.merge.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MergeTransitionHelpers;

class ForkChoicePayloadExecutor implements OptimisticExecutionPayloadExecutor {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final SignedBeaconBlock block;
  private final ExecutionEngineChannel executionEngine;
  private Optional<SafeFuture<ExecutePayloadResult>> result = Optional.empty();

  ForkChoicePayloadExecutor(
      final Spec spec,
      final SignedBeaconBlock block,
      final ExecutionEngineChannel executionEngine) {
    this.spec = spec;
    this.block = block;
    this.executionEngine = executionEngine;
  }

  public SafeFuture<ExecutePayloadResult> getExecutionResult() {
    return result.orElse(SafeFuture.completedFuture(ExecutePayloadResult.VALID));
  }

  @Override
  public boolean optimisticallyExecute(
      final ExecutionPayloadHeader latestExecutionPayloadHeader,
      final ExecutionPayload executionPayload) {
    if (executionPayload.isDefault()) {
      // We're still pre-merge so no payload to execute
      // Note that the BlockProcessor will have already failed if this is default and shouldn't be
      // because it check the parentRoot matches
      return true;
    }
    final MergeTransitionHelpers mergeTransitionHelpers =
        spec.atSlot(block.getSlot())
            .getMergeTransitionHelpers()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attempting to validate a merge block when spec does not have merge transition helpers"));

    if (latestExecutionPayloadHeader.isDefault()) {
      // This is the first filled payload, so need to check it's a valid merge block
      result =
          Optional.of(mergeTransitionHelpers.validateMergeBlock(executionEngine, executionPayload));
    } else {
      result =
          Optional.of(
              executionEngine
                  .executePayload(executionPayload)
                  .exceptionally(
                      error -> {
                        LOG.error("Error while executing payload", error);
                        return ExecutePayloadResult.SYNCING;
                      }));
    }
    return true;
  }
}
