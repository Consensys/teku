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

import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.GOSSIP;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.NOT_REQUIRED;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.CONSENSUS_FAILURE;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.FINAL_EQUIVOCATION_FAILURE;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.GOSSIP_FAILURE;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.SUCCESS;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode.ValidationResultSubCode;

public class BlockBroadcastValidatorImpl implements BlockBroadcastValidator {
  private final BlockGossipValidator blockGossipValidator;
  private final BroadcastValidationLevel broadcastValidationLevel;
  private final SafeFuture<Boolean> consensusValidationSuccessResult;
  private final SafeFuture<BroadcastValidationResult> broadcastValidationResult;

  private BlockBroadcastValidatorImpl(
      final BlockGossipValidator blockGossipValidator,
      final BroadcastValidationLevel broadcastValidationLevel) {
    this.blockGossipValidator = blockGossipValidator;
    this.broadcastValidationLevel = broadcastValidationLevel;
    this.consensusValidationSuccessResult = new SafeFuture<>();
    this.broadcastValidationResult = new SafeFuture<>();
  }

  public static BlockBroadcastValidatorImpl create(
      final SignedBeaconBlock block,
      final BlockGossipValidator blockGossipValidator,
      final BroadcastValidationLevel broadcastValidationLevel) {
    final BlockBroadcastValidatorImpl blockBroadcastValidator =
        new BlockBroadcastValidatorImpl(blockGossipValidator, broadcastValidationLevel);
    blockBroadcastValidator.buildValidationPipeline(block);
    return blockBroadcastValidator;
  }

  @Override
  public void onConsensusValidationSucceeded() {
    consensusValidationSuccessResult.complete(true);
  }

  @Override
  public void attachToBlockImport(final SafeFuture<BlockImportResult> blockImportResult) {
    switch (broadcastValidationLevel) {
      case NOT_REQUIRED, GOSSIP:
        // GOSSIP validation isn't dependent on block import result,
        // so not propagating exceptions to consensusValidationSuccessResult allow blocks\blobs
        // to be published even in case block import fails before gossip validation completes
        return;
      case CONSENSUS, CONSENSUS_AND_EQUIVOCATION:
        // Any successful block import will be considered as a consensus validation success, but
        // more importantly we propagate exceptions to the consensus validation, thus we capture any
        // early block import failures
        blockImportResult
            .thenApply(BlockImportResult::isSuccessful)
            .propagateTo(consensusValidationSuccessResult);
    }
  }

  @Override
  public SafeFuture<BroadcastValidationResult> getResult() {
    return broadcastValidationResult;
  }

  private void buildValidationPipeline(final SignedBeaconBlock block) {
    // validateBroadcast should not be called at all but let's cover the case for safety
    if (broadcastValidationLevel == NOT_REQUIRED) {
      broadcastValidationResult.complete(SUCCESS);
      consensusValidationSuccessResult.cancel(true);
      return;
    }

    // GOSSIP only validation
    SafeFuture<BroadcastValidationResult> validationPipeline =
        blockGossipValidator
            .validate(block, true)
            .thenApply(
                gossipValidationResult -> {
                  if (gossipValidationResult.isAccept()) {
                    return SUCCESS;
                  }
                  if (gossipValidationResult.isIgnore()
                      && gossipValidationResult
                          .getSubCode()
                          .map(
                              subCode ->
                                  subCode.equals(ValidationResultSubCode.IGNORE_ALREADY_SEEN))
                          .orElse(false)) {
                    return SUCCESS;
                  }
                  return GOSSIP_FAILURE;
                });

    if (broadcastValidationLevel == GOSSIP) {
      validationPipeline.propagateTo(broadcastValidationResult);
      consensusValidationSuccessResult.cancel(true);
      return;
    }

    // GOSSIP and CONSENSUS validation
    validationPipeline =
        validationPipeline.thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult != SUCCESS) {
                // forward gossip validation failure
                return SafeFuture.completedFuture(broadcastValidationResult);
              }
              return consensusValidationSuccessResult.thenApply(
                  consensusValidationSuccess -> {
                    if (consensusValidationSuccess) {
                      return SUCCESS;
                    }
                    return CONSENSUS_FAILURE;
                  });
            });

    if (broadcastValidationLevel == BroadcastValidationLevel.CONSENSUS) {
      validationPipeline.propagateTo(broadcastValidationResult);
      return;
    }

    // GOSSIP, CONSENSUS and additional EQUIVOCATION validation
    validationPipeline
        .thenApply(
            broadcastValidationResult -> {
              if (broadcastValidationResult != SUCCESS) {
                // forward gossip or consensus validation failure
                return broadcastValidationResult;
              }

              // perform final equivocation validation
              if (blockGossipValidator
                  .performBlockEquivocationCheck(block)
                  .equals(EquivocationCheckResult.EQUIVOCATING_BLOCK)) {
                return FINAL_EQUIVOCATION_FAILURE;
              }

              return SUCCESS;
            })
        .propagateTo(broadcastValidationResult);
  }
}
