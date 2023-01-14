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
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StubStorageUpdateChannel implements StorageUpdateChannel {

  @Override
  public SafeFuture<UpdateResult> onStorageUpdate(final StorageUpdate event) {
    return SafeFuture.completedFuture(UpdateResult.EMPTY);
  }

  @Override
  public SafeFuture<Void> onFinalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> onFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> onReconstructedFinalizedState(
      final BeaconState finalizedState, final Bytes32 blockRoot) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> onWeakSubjectivityUpdate(
      final WeakSubjectivityUpdate weakSubjectivityUpdate) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> onFinalizedDepositSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> onBlobsSidecar(final BlobsSidecar blobsSidecar) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> onBlobsSidecarRemoval(final SlotAndBlockRoot blobsSidecarKey) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public void onChainInitialized(final AnchorPoint initialAnchor) {}
}
