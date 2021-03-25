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

import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;

/**
 * Advanced block validator which uses {@link BatchSignatureVerifier} to verify all the BLS
 * signatures inside a block in an optimized batched way.
 */
class BatchBlockValidator implements BlockValidator {
  private final SpecConfig specConfig;
  private final BeaconStateUtil beaconStateUtil;
  private final BlockProcessor blockProcessor;
  private final BeaconStateAccessors beaconStateAccessors;
  private final BlockValidator simpleValidator;

  BatchBlockValidator(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final BlockProcessor blockProcessor,
      final BeaconStateAccessors beaconStateAccessors) {
    this.specConfig = specConfig;
    this.beaconStateUtil = beaconStateUtil;
    this.blockProcessor = blockProcessor;
    this.beaconStateAccessors = beaconStateAccessors;
    this.simpleValidator =
        new SimpleBlockValidator(specConfig, beaconStateUtil, blockProcessor, beaconStateAccessors);
  }

  @Override
  public BlockValidationResult validatePreState(
      BeaconState preState,
      SignedBeaconBlock block,
      IndexedAttestationCache indexedAttestationCache) {
    BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();
    SimpleBlockValidator blockValidator =
        new SimpleBlockValidator(
            specConfig, beaconStateUtil, blockProcessor, beaconStateAccessors, signatureVerifier);
    BlockValidationResult noBLSValidationResult =
        blockValidator.validatePreState(preState, block, indexedAttestationCache);
    // during the above validatePreState() call BatchSignatureVerifier just collected
    // a bunch of signatures to be verified in optimized batched way on the following step
    if (!noBLSValidationResult.isValid()) {
      // something went wrong aside of signatures verification
      return noBLSValidationResult;
    } else {
      boolean batchBLSResult = signatureVerifier.batchVerify();
      if (!batchBLSResult) {
        // validate again naively to get exact invalid signature
        return simpleValidator.validatePreState(preState, block, indexedAttestationCache);
      } else {
        return BlockValidationResult.SUCCESSFUL;
      }
    }
  }

  @Override
  public BlockValidationResult validatePostState(BeaconState postState, SignedBeaconBlock block) {
    return simpleValidator.validatePostState(postState, block);
  }
}
