/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.function.Consumer;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;

public abstract class BlindBlockUtil {
  public SafeFuture<SignedBeaconBlock> unblindSignedBeaconBlock(
      final SignedBeaconBlock signedBeaconBlock,
      final Consumer<SignedBeaconBlockUnblinder> blockUnblinder) {
    final SignedBeaconBlockUnblinder beaconBlockUnblinder =
        createSignedBeaconBlockUnblinder(signedBeaconBlock);
    blockUnblinder.accept(beaconBlockUnblinder);
    return beaconBlockUnblinder.unblind();
  }

  public SignedBeaconBlock blindSignedBeaconBlock(final SignedBeaconBlock signedBeaconBlock) {
    return getSignedBeaconBlockBlinder().blind(signedBeaconBlock);
  }

  protected abstract SignedBeaconBlockUnblinder createSignedBeaconBlockUnblinder(
      final SignedBeaconBlock signedBeaconBlock);

  protected abstract SignedBeaconBlockBlinder getSignedBeaconBlockBlinder();
}
