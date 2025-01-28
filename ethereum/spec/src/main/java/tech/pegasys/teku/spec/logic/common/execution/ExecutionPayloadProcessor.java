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

package tech.pegasys.teku.spec.logic.common.execution;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionPayloadProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface ExecutionPayloadProcessor {

  BeaconState processAndVerifyExecutionPayload(
      BeaconState preState,
      SignedExecutionPayloadEnvelope signedEnvelope,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException;

  boolean verifyExecutionPayloadEnvelopeSignature(
      BeaconState state, SignedExecutionPayloadEnvelope signedEnvelope);

  BeaconState processUnsignedExecutionPayload(
      BeaconState preState,
      ExecutionPayloadEnvelope envelope,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadProcessingException;

  void processExecutionPayload(
      MutableBeaconState state,
      ExecutionPayloadEnvelope envelope,
      Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws ExecutionPayloadProcessingException;

  NewPayloadRequest computeNewPayloadRequest(BeaconState state, ExecutionPayloadEnvelope envelope);
}
