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
    consensusValidationSuccessResult = new SafeFuture<>();
    broadcastValidationResult = new SafeFuture<>();
  }

  public static BlockBroadcastValidatorImpl create(
      final SignedBeaconBlock block,
      final BlockGossipValidator blockGossipValidator,
      final BroadcastValidationLevel broadcastValidationLevel) {
    BlockBroadcastValidatorImpl blockBroadcastValidator =
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
    blockImportResult
        .thenApply(BlockImportResult::isSuccessful)
        .propagateTo(consensusValidationSuccessResult);
  }

  @Override
  public SafeFuture<BroadcastValidationResult> getResult() {
    return broadcastValidationResult;
  }

  private void buildValidationPipeline(final SignedBeaconBlock block) {
    // validateBroadcast should not be called at all but let's cover the case for safety
    if (broadcastValidationLevel == BroadcastValidationLevel.NOT_REQUIRED) {
      broadcastValidationResult.complete(BroadcastValidationResult.SUCCESS);
    }

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
      validationPipeline.propagateTo(broadcastValidationResult);
      return;
    }

    // GOSSIP and CONSENSUS validation
    validationPipeline =
        validationPipeline.thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult != BroadcastValidationResult.SUCCESS) {
                // forward gossip validation failure
                return SafeFuture.completedFuture(broadcastValidationResult);
              }
              return consensusValidationSuccessResult.thenApply(
                  consensusValidationSuccess -> {
                    if (consensusValidationSuccess) {
                      return BroadcastValidationResult.SUCCESS;
                    }
                    return BroadcastValidationResult.CONSENSUS_FAILURE;
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
              if (broadcastValidationResult != BroadcastValidationResult.SUCCESS) {
                // forward gossip or consensus validation failure
                return broadcastValidationResult;
              }

              // perform final equivocation validation
              if (blockGossipValidator.blockIsFirstBlockWithValidSignatureForSlot(block)) {
                return BroadcastValidationResult.SUCCESS;
              }

              return BroadcastValidationResult.FINAL_EQUIVOCATION_FAILURE;
            })
        .propagateTo(broadcastValidationResult);
  }
}
