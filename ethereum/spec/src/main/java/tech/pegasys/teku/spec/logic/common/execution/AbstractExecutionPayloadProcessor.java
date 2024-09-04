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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.ExecutionPayloadProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public abstract class AbstractExecutionPayloadProcessor implements ExecutionPayloadProcessor {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public BeaconState processAndVerifyExecutionPayload(
      final BeaconState preState,
      final SignedExecutionPayloadEnvelope signedEnvelope,
      final Optional<? extends OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    if (!verifyExecutionPayloadEnvelopeSignature(preState, signedEnvelope)) {
      throw new StateTransitionException(
          "Signature verification failed for signed envelope with beacon block root "
              + signedEnvelope.getMessage().getBeaconBlockRoot());
    }
    final BeaconState postState =
        preState.updated(
            state -> {
              try {
                processExecutionPayload(state, signedEnvelope.getMessage(), payloadExecutor);
              } catch (ExecutionPayloadProcessingException ex) {
                LOG.warn(
                    "State transition error while importing execution payload with beacon block root "
                        + signedEnvelope.getMessage().getBeaconBlockRoot(),
                    ex);
                throw new StateTransitionException(ex);
              }
            });
    if (signedEnvelope.getMessage().getStateRoot().equals(postState.hashTreeRoot())) {
      throw new StateTransitionException(
          "State root of the signed envelope does not match the post-processing state root");
    }
    return postState;
  }

  // Catch generic errors and wrap them in an ExecutionPayloadProcessingException
  protected void safelyProcess(final ExecutionPayloadProcessingAction action)
      throws ExecutionPayloadProcessingException {
    try {
      action.run();
    } catch (Exception ex) {
      LOG.warn("Failed to process execution payload", ex);
      throw new ExecutionPayloadProcessingException(ex);
    }
  }

  protected interface ExecutionPayloadProcessingAction {
    void run() throws ExecutionPayloadProcessingException;
  }
}
