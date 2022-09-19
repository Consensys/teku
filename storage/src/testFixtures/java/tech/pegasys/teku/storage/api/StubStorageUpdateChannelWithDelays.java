/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.api;

import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StubStorageUpdateChannelWithDelays implements StorageUpdateChannel {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  public StubAsyncRunner getAsyncRunner() {
    return asyncRunner;
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(StorageUpdate event) {
    return asyncRunner.runAsync(() -> SafeFuture.completedFuture(UpdateResult.EMPTY));
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
    return asyncRunner.runAsync(() -> SafeFuture.COMPLETE);
  }

  @Override
  public SafeFuture<Void> onFinalizedState(BeaconState finalizedState, Bytes32 blockRoot) {
    return asyncRunner.runAsync(() -> SafeFuture.COMPLETE);
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return asyncRunner.runAsync(() -> SafeFuture.COMPLETE);
  }

  @Override
  public SafeFuture<Void> onFinalizedDepositSnapshot(DepositTreeSnapshot depositTreeSnapshot) {
    return asyncRunner.runAsync(() -> SafeFuture.COMPLETE);
  }

  @Override
  public void onChainInitialized(AnchorPoint initialAnchor) {}
}
