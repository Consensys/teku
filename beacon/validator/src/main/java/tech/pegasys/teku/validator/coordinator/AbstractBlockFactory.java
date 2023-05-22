/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.validator.coordinator;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public abstract class AbstractBlockFactory implements BlockFactory {

  protected final Spec spec;
  protected final BlockOperationSelectorFactory operationSelector;

  protected AbstractBlockFactory(
      final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    this.spec = spec;
    this.operationSelector = operationSelector;
  }

  @Override
  public SafeFuture<SignedBeaconBlock> unblindSignedBeaconBlockIfBlinded(
      final SignedBeaconBlock blindedSignedBeaconBlock) {
    if (blindedSignedBeaconBlock.isBlinded()) {
      return spec.unblindSignedBeaconBlock(
          blindedSignedBeaconBlock, operationSelector.createUnblinderSelector());
    }
    return SafeFuture.completedFuture(blindedSignedBeaconBlock);
  }
}
