/*
 * Copyright Consensys Software Inc., 2023
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

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;

public class BlockValidator {

  private final BlockGossipValidator blockGossipValidator;

  public BlockValidator(final BlockGossipValidator blockGossipValidator) {
    this.blockGossipValidator = blockGossipValidator;
  }

  public SafeFuture<InternalValidationResult> validateGossip(final SignedBeaconBlock block) {
    return blockGossipValidator.validate(block, false);
  }

  public SafeFuture<BroadcastValidationResult> validateBroadcast(
      final SignedBeaconBlock block,
      final BroadcastValidationLevel broadcastValidationLevel,
      final SafeFuture<BlockImportResult> consensusValidationResult) {

    // GOSSIP only validation
    SafeFuture<BroadcastValidationResult> validationPipeline =
        blockGossipValidator
            .validate(block, true)
            .thenApply(
                gossipValidationResult -> {
                  if (gossipValidationResult.isAccept()) {
                    return BroadcastValidationResult.SUCCESS;
                  }
                  return BroadcastValidationResult.GOSSIP_FAILURE;
                });

    if (broadcastValidationLevel == BroadcastValidationLevel.GOSSIP) {
      return validationPipeline;
    }

    // GOSSIP and CONSENSUS validation
    validationPipeline =
        validationPipeline.thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult != BroadcastValidationResult.SUCCESS) {
                // forward gossip validation failure
                return SafeFuture.completedFuture(broadcastValidationResult);
              }
              return consensusValidationResult.thenApply(
                  consensusValidation -> {
                    if (consensusValidation.isSuccessful()) {
                      return BroadcastValidationResult.SUCCESS;
                    }
                    return BroadcastValidationResult.CONSENSUS_FAILURE;
                  });
            });

    if (broadcastValidationLevel == BroadcastValidationLevel.CONSENSUS) {
      return validationPipeline;
    }

    // GOSSIP, CONSENSUS and additional EQUIVOCATION validation
    return validationPipeline.thenApply(
        broadcastValidationResult -> {
          if (broadcastValidationResult != BroadcastValidationResult.SUCCESS) {
            // forward gossip or consensus validation failure
            return broadcastValidationResult;
          }

          // perform final equivocation validation
          if (blockGossipValidator.blockIsFirstBlockWithValidSignatureForSlot(block)) {
            return BroadcastValidationResult.SUCCESS;
          }

          return BroadcastValidationResult.FINAL_EQUIVOCATION_FAILURE;
        });
  }

  public enum BroadcastValidationResult {
    SUCCESS,
    GOSSIP_FAILURE,
    CONSENSUS_FAILURE,
    FINAL_EQUIVOCATION_FAILURE
  }
}
