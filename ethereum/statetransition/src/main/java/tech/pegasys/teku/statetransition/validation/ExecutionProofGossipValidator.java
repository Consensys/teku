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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.config.Constants.MAX_EXECUTION_PROOF_SUBNETS;

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;

public class ExecutionProofGossipValidator {
  private static final Logger LOG = LogManager.getLogger();

  private final Set<ExecutionProof> receivedValidExecutionProofSet;

  public static ExecutionProofGossipValidator create() {
    return new ExecutionProofGossipValidator(
        LimitedSet.createSynchronized(MAX_EXECUTION_PROOF_SUBNETS.intValue()));
  }

  public ExecutionProofGossipValidator(final Set<ExecutionProof> receivedValidExecutionProofSet) {

    this.receivedValidExecutionProofSet = receivedValidExecutionProofSet;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ExecutionProof executionProof, final UInt64 subnetId) {

    // TODO need to check for other validations done in the prototype and spec
    if (executionProof.getSubnetId().longValue() != subnetId.longValue()) {
      LOG.trace(
          "ExecutionProof for block root {} does not match the gossip subnetId",
          executionProof.getBlockRoot());
      return SafeFuture.completedFuture(InternalValidationResult.reject("SubnetId mismatch"));
    }

    if (receivedValidExecutionProofSet.contains(executionProof)) {
      // Already seen and valid
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    // Validated the execution proof
    LOG.trace(
        "Received and validated execution proof for block root {}", executionProof.getBlockRoot());
    receivedValidExecutionProofSet.add(executionProof);
    return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
  }
}
