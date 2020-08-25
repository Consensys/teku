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

public class NoOpBlockValidator implements BlockValidator {

  @Override
  public SafeFuture<BlockValidationResult> validatePreState(
      BeaconState preState,
      SignedBeaconBlock block,
      IndexedAttestationProvider indexedAttestationProvider) {
    return SafeFuture.completedFuture(new BlockValidationResult(true));
  }

  @Override
  public SafeFuture<BlockValidationResult> validatePostState(
      BeaconState postState, SignedBeaconBlock block) {
    return SafeFuture.completedFuture(new BlockValidationResult(true));
  }
}
