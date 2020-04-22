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

package tech.pegasys.artemis.core.blockvalidator;

import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface BlockValidator {

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

  BlockValidator NOP = new NopBlockValidator();

  SafeFuture<BlockValidationResult> validatePreState(BeaconState preState, SignedBeaconBlock block);

  SafeFuture<BlockValidationResult> validatePostState(
      BeaconState postState, SignedBeaconBlock block);

  default SafeFuture<BlockValidationResult> validate(
      BeaconState preState, SignedBeaconBlock block, BeaconState postState) {
    SafeFuture<BlockValidationResult> preFut = validatePreState(preState, block);
    SafeFuture<BlockValidationResult> postFut = validatePostState(postState, block);
    return preFut.thenCombine(postFut, (pre, post) -> !pre.isValid() ? pre : post);
  }
}
