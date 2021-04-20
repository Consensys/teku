/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;

/**
 * Advanced block validator which uses {@link BatchSignatureVerifier} to verify all the BLS
 * signatures inside a block in an optimized batched way.
 */
public class BlockValidator {

  private final BlockProcessor blockProcessor;

  public BlockValidator(final BlockProcessor blockProcessor) {
    this.blockProcessor = blockProcessor;
  }

  public BlockValidationResult validatePreState(
      BeaconState preState,
      SignedBeaconBlock block,
      IndexedAttestationCache indexedAttestationCache) {
    BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();
    BlockValidationResult noBLSValidationResult =
        blockProcessor.verifySignatures(
            preState, block, indexedAttestationCache, signatureVerifier);
    // during the above validatePreState() call BatchSignatureVerifier just collected
    // a bunch of signatures to be verified in optimized batched way on the following step
    if (!noBLSValidationResult.isValid()) {
      // something went wrong aside of signatures verification
      return noBLSValidationResult;
    } else {
      if (!signatureVerifier.batchVerify()) {
        // validate again naively to get exact invalid signature
        return blockProcessor.verifySignatures(
            preState, block, indexedAttestationCache, BLSSignatureVerifier.SIMPLE);
      }
      return BlockValidationResult.SUCCESSFUL;
    }
  }

  public BlockValidationResult validatePostState(BeaconState postState, SignedBeaconBlock block) {
    return blockProcessor.validatePostState(postState, block);
  }

  /**
   * Combines {@link #validatePreState(BeaconState, SignedBeaconBlock, IndexedAttestationCache)} and
   * {@link #validatePostState(BeaconState, SignedBeaconBlock)}
   *
   * @return
   */
  public BlockValidationResult validate(
      BeaconState preState,
      SignedBeaconBlock block,
      BeaconState postState,
      IndexedAttestationCache indexedAttestationCache) {
    BlockValidationResult preResult = validatePreState(preState, block, indexedAttestationCache);
    if (!preResult.isValid()) {
      return preResult;
    }

    return validatePostState(postState, block);
  }
}
