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

package tech.pegasys.teku.spec.logic.common.execution;

import java.util.Optional;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionPayloadProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface ExecutionPayloadProcessor {

  BeaconState processAndVerifyExecutionPayload(
      SignedExecutionPayloadEnvelope signedEnvelope,
      BeaconState blockState,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException;

  /**
   * Processes the given execution payload on top of {@code state} and optionally validates the
   * execution payload
   *
   * @param signedEnvelope The execution payload to be processed
   * @param state The preState on which this execution payload should be processed, this preState
   *     must be the state of the block of the current slot. The state will be mutated if processing
   *     was successful.
   * @param signatureVerifier The signature verifier to use
   * @param payloadExecutor the optimistic payload executor to begin execution with
   * @param verify enable pre and post verification
   * @throws ExecutionPayloadProcessingException If the execution payload is invalid or cannot be
   *     processed
   */
  void processExecutionPayload(
      SignedExecutionPayloadEnvelope signedEnvelope,
      MutableBeaconState state,
      BLSSignatureVerifier signatureVerifier,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      boolean verify)
      throws ExecutionPayloadProcessingException;

  void processUnsignedExecutionPayload(
      MutableBeaconState state,
      ExecutionPayloadEnvelope envelope,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadProcessingException;
}
