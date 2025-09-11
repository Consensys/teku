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

package tech.pegasys.teku.statetransition.executionproofs;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.validation.ExecutionProofGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class ExecutionProofManagerImpl implements ExecutionProofManager {

  final ExecutionProofGossipValidator executionProofGossipValidator;

  public ExecutionProofManagerImpl(
      final ExecutionProofGossipValidator executionProofGossipValidator) {
    this.executionProofGossipValidator = executionProofGossipValidator;
  }

  @Override
  public void onExecutionProofPublish(
      final ExecutionProof executionProof, final RemoteOrigin remoteOrigin) {}

  @Override
  public SafeFuture<InternalValidationResult> onExecutionProofGossip(
      final ExecutionProof executionProof, final Optional<UInt64> arrivalTimestamp) {
    return null;
  }

  @Override
  public void subscribeToValidExecutionProofs(
      final ValidExecutionProofListener executionProofListener) {}
}
