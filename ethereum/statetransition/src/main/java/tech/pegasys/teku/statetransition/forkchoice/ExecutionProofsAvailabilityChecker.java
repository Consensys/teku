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

package tech.pegasys.teku.statetransition.forkchoice;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

public class ExecutionProofsAvailabilityChecker implements AvailabilityChecker<ExecutionProof> {
  private static final Logger LOG = LogManager.getLogger();
  private final ExecutionProofManager executionProofManager;
  private final SafeFuture<DataAndValidationResult<ExecutionProof>> validationResult =
      new SafeFuture<>();
  private SignedBeaconBlock block;
  private AvailabilityChecker<?> delegate;

  public ExecutionProofsAvailabilityChecker(
      final ExecutionProofManager executionProofManager,
      final SignedBeaconBlock block,
      final AvailabilityChecker<?> delegate) {
    this.executionProofManager = executionProofManager;
    this.delegate = delegate;
    this.block = block;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    delegate.initiateDataAvailabilityCheck();
    executionProofManager
        .validateBlockWithExecutionProofs(block)
        // should probably use a timeout based on slot time
        .orTimeout(Duration.ofSeconds(10))
        .propagateTo(validationResult);
    return true;
  }

  @Override
  public SafeFuture<DataAndValidationResult<ExecutionProof>> getAvailabilityCheckResult() {
    return delegate
        .getAvailabilityCheckResult()
        .thenCompose(
            daResult -> {
              LOG.debug("Delegate Availability result: {}", daResult.validationResult());
              if (daResult.isSuccess()) {
                LOG.debug(
                    "Blob/DataColumn availability valid, proceeding to execution proofs validation");
                return validationResult;
              } else {
                List<ExecutionProof> emptyList = Collections.emptyList();
                return SafeFuture.completedFuture(
                    new DataAndValidationResult<>(
                        AvailabilityValidationResult.NOT_AVAILABLE, emptyList, daResult.cause()));
              }
            });
  }
}
