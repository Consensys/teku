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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionPayloadProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.statetransition.executionpayloadvalidator.ExecutionPayloadValidationResult;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public abstract class AbstractExecutionPayloadProcessor implements ExecutionPayloadProcessor {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public BeaconState processAndVerifyExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final BeaconState blockState,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    try {
      return blockState.updated(
          preState ->
              processExecutionPayload(
                  signedEnvelope, preState, BLSSignatureVerifier.SIMPLE, payloadExecutor, true));
    } catch (final IllegalArgumentException | ExecutionPayloadProcessingException ex) {
      LOG.warn(
          String.format(
              "State transition error while importing execution payload (builder index: %s, slot: %s, block root: %s)",
              signedEnvelope.getMessage().getBuilderIndex(),
              signedEnvelope.getMessage().getSlot(),
              signedEnvelope.getBeaconBlockRoot()),
          ex);
      throw new StateTransitionException(ex);
    }
  }

  // process_execution_payload
  @Override
  public void processExecutionPayload(
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final MutableBeaconState state,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor,
      final boolean verify)
      throws ExecutionPayloadProcessingException {
    if (verify) {
      final ExecutionPayloadValidationResult preValidationResult =
          validateExecutionPayloadPreProcessing(state, signedEnvelope, signatureVerifier);
      if (!preValidationResult.isValid()) {
        throw new ExecutionPayloadProcessingException(preValidationResult.getFailureReason());
      }
    }

    processUnsignedExecutionPayload(state, signedEnvelope.getMessage(), payloadExecutor);

    if (verify) {
      final ExecutionPayloadValidationResult postValidationResult =
          validateExecutionPayloadPostProcessing(state, signedEnvelope.getMessage());
      if (!postValidationResult.isValid()) {
        throw new ExecutionPayloadProcessingException(postValidationResult.getFailureReason());
      }
    }
  }

  protected abstract ExecutionPayloadValidationResult validateExecutionPayloadPreProcessing(
      BeaconState preState,
      SignedExecutionPayloadEnvelope signedEnvelope,
      BLSSignatureVerifier signatureVerifier);

  protected abstract ExecutionPayloadValidationResult validateExecutionPayloadPostProcessing(
      BeaconState postState, ExecutionPayloadEnvelope envelope);
}
