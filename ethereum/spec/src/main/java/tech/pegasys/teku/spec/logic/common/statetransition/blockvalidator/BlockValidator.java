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
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProcessorUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;

/**
 * Dedicated class which performs block validation (apart from {@link BlockProcessorUtil} The
 * validation may be performed either synchronously (then the methods return completed futures) or
 * asynchronously.
 */
public interface BlockValidator {

  /** Block validator which just returns OK result without any validations */
  BlockValidator NOOP = new NoOpBlockValidator();

  static BlockValidator standard(
      final SpecConfig specConfig,
      final BeaconStateUtil beaconStateUtil,
      final BlockProcessorUtil blockProcessorUtil,
      final ValidatorsUtil validatorsUtil) {
    return new BatchBlockValidator(specConfig, beaconStateUtil, blockProcessorUtil, validatorsUtil);
  }

  /**
   * Validates the block against the state prior to block processing
   *
   * <p>This normally includes validating all signatures, checking validity of attestations,
   * slashings, etc.
   *
   * @param preState Normally the state with the slot equal to the block's slot However
   *     implementations may allow to pass earlier or later state which has the necessary
   *     information (randao history) to recover committees for the block slot, attestations slots,
   *     etc
   * @param block Block to be validated
   * @param indexedAttestationCache
   * @return Result promise
   */
  BlockValidationResult validatePreState(
      BeaconState preState,
      SignedBeaconBlock block,
      IndexedAttestationCache indexedAttestationCache);

  /**
   * Validates the block against the state after block processing
   *
   * <p>This is normally calculating the state hash root and comparing it to the state root
   * specified in the block
   *
   * @param postState beacon state right after applying block transition
   * @param block Block to be validated
   * @return Result promise
   */
  BlockValidationResult validatePostState(BeaconState postState, SignedBeaconBlock block);

  /**
   * Combines {@link #validatePreState(BeaconState, SignedBeaconBlock, IndexedAttestationCache)} and
   * {@link #validatePostState(BeaconState, SignedBeaconBlock)}
   *
   * @return
   */
  default BlockValidationResult validate(
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
