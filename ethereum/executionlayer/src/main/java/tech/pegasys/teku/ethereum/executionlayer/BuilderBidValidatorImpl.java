/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public class BuilderBidValidatorImpl implements BuilderBidValidator {
  private final EventLogger eventLogger;

  public BuilderBidValidatorImpl(final EventLogger eventLogger) {
    this.eventLogger = eventLogger;
  }

  @Override
  public ExecutionPayloadHeader validateAndGetPayloadHeader(
      final Spec spec,
      final SignedBuilderBid signedBuilderBid,
      final SignedValidatorRegistration signedValidatorRegistration,
      final BeaconState state)
      throws BuilderBidValidationException {

    // validating Bid Signature
    final Bytes signingRoot =
        spec.computeBuilderApplicationSigningRoot(state.getSlot(), signedBuilderBid.getMessage());

    if (!BLS.verify(
        signedBuilderBid.getMessage().getPublicKey(),
        signingRoot,
        signedBuilderBid.getSignature())) {
      throw new BuilderBidValidationException("Invalid Bid Signature");
    }

    final ExecutionPayloadHeader executionPayloadHeader =
        signedBuilderBid.getMessage().getExecutionPayloadHeader();

    // validating payload wrt consensus
    try {
      spec.atSlot(state.getSlot())
          .getBlockProcessor()
          .validateExecutionPayload(
              state, executionPayloadHeader, Optional.empty(), Optional.empty());
    } catch (BlockProcessingException e) {
      throw new BuilderBidValidationException(
          "Invalid proposed payload with respect to consensus.", e);
    }

    // checking payload gas limit
    final UInt64 parentGasLimit =
        state.toVersionBellatrix().orElseThrow().getLatestExecutionPayloadHeader().getGasLimit();
    final UInt64 preferredGasLimit = signedValidatorRegistration.getMessage().getGasLimit();
    final UInt64 proposedGasLimit = executionPayloadHeader.getGasLimit();

    if (parentGasLimit.equals(preferredGasLimit) && proposedGasLimit.equals(parentGasLimit)) {
      return executionPayloadHeader;
    }

    if (preferredGasLimit.isGreaterThan(parentGasLimit)
        && proposedGasLimit.isGreaterThan(parentGasLimit)) {
      return executionPayloadHeader;
    }

    if (preferredGasLimit.isLessThan(parentGasLimit)
        && proposedGasLimit.isLessThan(parentGasLimit)) {
      return executionPayloadHeader;
    }

    eventLogger.builderBidNotHonouringGasLimit(parentGasLimit, proposedGasLimit, preferredGasLimit);

    return executionPayloadHeader;
  }
}
