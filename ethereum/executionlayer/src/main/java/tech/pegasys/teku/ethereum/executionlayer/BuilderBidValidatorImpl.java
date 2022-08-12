/*
 * Copyright ConsenSys Software Inc., 2022
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public class BuilderBidValidatorImpl implements BuilderBidValidator {
  private static final Logger LOG = LogManager.getLogger();
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

    final ValidatorRegistration validatorRegistration = signedValidatorRegistration.getMessage();

    // Show a debug message if the fee recipient in the builder bid differs from the fee recipient
    // specified in the validator registration. This is expected behavior and is not a clear sign of
    // a dishonest builder. They can build a block in advance of knowing who the fee recipient is
    // (giving them more time) using their own fee recipient, then just insert one last transaction
    // into the block to transfer the payment to the requested fee recipient. It probably indicates
    // a smart builder optimizing things well.
    final Eth1Address suggestedFeeRecipient = validatorRegistration.getFeeRecipient();
    if (!executionPayloadHeader.getFeeRecipient().equals(suggestedFeeRecipient)) {
      final Eth1Address payloadHeaderFeeRecipient =
          Eth1Address.fromBytes(executionPayloadHeader.getFeeRecipient().getWrappedBytes());
      LOG.debug(
          "The fee recipient in the builder bid ({}) is not the same as the configured fee recipient ({}) for validator {}."
              + " This is expected behavior. Most likely a builder optimization.",
          payloadHeaderFeeRecipient,
          suggestedFeeRecipient,
          validatorRegistration.getPublicKey());
    }

    // checking payload gas limit
    final UInt64 parentGasLimit =
        state.toVersionBellatrix().orElseThrow().getLatestExecutionPayloadHeader().getGasLimit();
    final UInt64 preferredGasLimit = validatorRegistration.getGasLimit();
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
