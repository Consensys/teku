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

package tech.pegasys.teku.storage.storageSystem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

public class DelayedStorageUpdateChannel implements StorageUpdateChannel {
  private final List<Runnable> pendingActions = new ArrayList<>();
  private final StorageUpdateChannel delegate;

  public DelayedStorageUpdateChannel(final StorageUpdateChannel delegate) {
    this.delegate = delegate;
  }

  public void completeNextAction() {
    pendingActions.remove(0).run();
  }

  public void completeAllActions() {
    while (!pendingActions.isEmpty()) {
      completeNextAction();
    }
  }

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    return delay(() -> delegate.onStorageUpdate(event));
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
    return delay(() -> delegate.onFinalizedBlocks(finalizedBlocks));
  }

  @Override
  public SafeFuture<Void> onFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    return delay(() -> delegate.onFinalizedState(finalizedState, blockRoot));
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return delay(() -> delegate.onWeakSubjectivityUpdate(weakSubjectivityUpdate));
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {
    delay(
            () -> {
              delegate.onChainInitialized(initialAnchor);
              return SafeFuture.COMPLETE;
            })
        .ifExceptionGetsHereRaiseABug();
  }

  private <T> SafeFuture<T> delay(final Supplier<SafeFuture<T>> action) {
    final SafeFuture<T> result = new SafeFuture<>();
    pendingActions.add(() -> action.get().propagateTo(result));
    return result;
  }
}
