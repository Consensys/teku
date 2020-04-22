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

public class BatchBlockValidator implements BlockValidator {

  @Override
  public SafeFuture<BlockValidationResult> validatePreState(
      BeaconState preState, SignedBeaconBlock block) {
    BatchSignatureVerifier signatureVerifier = new BatchSignatureVerifier();
    SimpleBlockValidator blockValidator =
        new SimpleBlockValidator(true, true, true, signatureVerifier);
    SafeFuture<BlockValidationResult> noBLSValidationResultFut =
        blockValidator.validatePreState(preState, block);
    // during the above validatePreState() call BatchSignatureVerifier just collected
    // a bunch of signatures to be verified in optimized batched way on the following step
    if (!noBLSValidationResultFut.join().isValid()) {
      // something went wrong aside of signatures verification
      return noBLSValidationResultFut;
    } else {
      boolean batchBLSResult = signatureVerifier.batchVerify();
      if (!batchBLSResult) {
        // validate again naively to get exact invalid signature
        return new SimpleBlockValidator().validatePreState(preState, block);
      } else {
        return SafeFuture.completedFuture(new BlockValidationResult(true));
      }
    }
  }

  @Override
  public SafeFuture<BlockValidationResult> validatePostState(
      BeaconState postState, SignedBeaconBlock block) {
    return new SimpleBlockValidator().validatePostState(postState, block);
  }
}
