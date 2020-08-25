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

package tech.pegasys.teku.core.blockvalidator;

import tech.pegasys.teku.core.lookup.IndexedAttestationProvider;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Dedicated class which performs block validation (apart from {@link
 * tech.pegasys.teku.core.BlockProcessorUtil} The validation may be performed either synchronously
 * (then the methods return completed futures) or asynchronously.
 */
public interface BlockValidator {

  /** Represents block validation result which may contain reason exception in case of a failure */
  class BlockValidationResult {
    private final boolean isValid;
    private final Exception reason;

    public BlockValidationResult(Exception reason) {
      this.isValid = false;
      this.reason = reason;
    }

    public BlockValidationResult(boolean isValid) {
      this.isValid = isValid;
      reason = null;
    }

    public boolean isValid() {
      return isValid;
    }

    public Exception getReason() {
      return reason;
    }
  }

  /** Block validator which just returns OK result without any validations */
  BlockValidator NOOP = new NoOpBlockValidator();

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
   * @param indexedAttestationProvider the provider to use to calculate indexed attestations
   * @return Result promise
   */
  SafeFuture<BlockValidationResult> validatePreState(
      BeaconState preState,
      SignedBeaconBlock block,
      IndexedAttestationProvider indexedAttestationProvider);

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
  SafeFuture<BlockValidationResult> validatePostState(
      BeaconState postState, SignedBeaconBlock block);

  /**
   * Combines {@link #validatePreState(BeaconState, SignedBeaconBlock, IndexedAttestationProvider)}
   * and {@link #validatePostState(BeaconState, SignedBeaconBlock)}
   */
  default SafeFuture<BlockValidationResult> validate(
      BeaconState preState,
      SignedBeaconBlock block,
      BeaconState postState,
      IndexedAttestationProvider indexedAttestationProvider) {
    SafeFuture<BlockValidationResult> preFut =
        validatePreState(preState, block, indexedAttestationProvider);
    SafeFuture<BlockValidationResult> postFut = validatePostState(postState, block);
    return preFut.thenCombine(postFut, (pre, post) -> !pre.isValid() ? pre : post);
  }
}
