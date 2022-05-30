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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

@FunctionalInterface
public interface BuilderBidValidator {
  BuilderBidValidator NOOP =
      (spec, signedBuilderBid, signedValidatorRegistration, state) ->
          signedBuilderBid.getMessage().getExecutionPayloadHeader();

  BuilderBidValidator VALIDATOR =
      (spec, signedBuilderBid, signedValidatorRegistration, state) -> {
        // validating Bid Signature
        final Bytes signingRoot =
            spec.computeBuilderApplicationSigningRoot(
                state.getSlot(), signedBuilderBid.getMessage());

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

        // validating payload gas limit
        final UInt64 parentGasLimit =
            state
                .toVersionBellatrix()
                .orElseThrow()
                .getLatestExecutionPayloadHeader()
                .getGasLimit();
        final UInt64 preferredGasLimit = signedValidatorRegistration.getMessage().getGasLimit();

        final UInt64 maxGasLimitDelta = UInt64.fromLongBits(parentGasLimit.longValue() >>> 10);

        final UInt64 expectedGasLimit;
        final int diff = parentGasLimit.compareTo(preferredGasLimit);
        if (diff < 0) {
          // parentGasLimit < preferredGasLimit
          expectedGasLimit = parentGasLimit.plus(maxGasLimitDelta).min(preferredGasLimit);
        } else if (diff == 0) {
          // parentGasLimit == preferredGasLimit
          expectedGasLimit = parentGasLimit;
        } else {
          // parentGasLimit > preferredGasLimit
          expectedGasLimit = parentGasLimit.minus(maxGasLimitDelta).max(preferredGasLimit);
        }

        final UInt64 proposedGasLimit = executionPayloadHeader.getGasLimit();

        if (!proposedGasLimit.equals(expectedGasLimit)) {
          throw new BuilderBidValidationException(
              "Proposed gasLimit not honouring validator preference. Parent gasLimit: "
                  + parentGasLimit
                  + " Proposed gasLimit: "
                  + proposedGasLimit
                  + " Preferred gasLimit: "
                  + preferredGasLimit
                  + " Expected proposed gasLimit: "
                  + expectedGasLimit);
        }

        return executionPayloadHeader;
      };

  ExecutionPayloadHeader validateAndGetPayloadHeader(
      final Spec spec,
      final SignedBuilderBid signedBuilderBid,
      final SignedValidatorRegistration signedValidatorRegistration,
      final BeaconState state)
      throws BuilderBidValidationException;
}
