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

package tech.pegasys.teku.beacon.sync.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.gossip.FetchBlockResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ResourceUnavailableException;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;

public class FetchBlockAndBlobsSidecarTaskTest extends AbstractFetchBlockTask {

  @Test
  public void run_successful() {
    final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
        dataStructureUtil.randomConsistentSignedBeaconBlockAndBlobsSidecar(UInt64.valueOf(10));
    final SignedBeaconBlock block = blockAndBlobsSidecar.getSignedBeaconBlock();
    final BlobsSidecar blobsSidecar = blockAndBlobsSidecar.getBlobsSidecar();

    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();

    final FetchBlockTask task = new FetchBlockAndBlobsSidecarTask(eth2P2PNetwork, blockRoot);

    assertThat(task.getBlockRoot()).isEqualTo(blockRoot);

    final Eth2Peer peer = registerNewPeer(1);

    when(peer.requestBlockAndBlobsSidecarByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndBlobsSidecar)));

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isTrue();
    assertThat(fetchBlockResult.getBlock()).isEqualTo(block);
    assertThat(fetchBlockResult.getBlobsSidecar()).hasValue(blobsSidecar);
  }

  @Test
  public void run_fail() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    final FetchBlockTask task = new FetchBlockAndBlobsSidecarTask(eth2P2PNetwork, blockRoot);

    assertThat(task.getBlockRoot()).isEqualTo(blockRoot);

    final Eth2Peer peer = registerNewPeer(1);

    // some exception other than RESOURCE_UNAVAILABLE
    when(peer.requestBlockAndBlobsSidecarByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new DeserializationFailedException()));

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isFalse();
    assertThat(fetchBlockResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
  }

  @Test
  public void run_failAndTryWithOldRpcMethod() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);

    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();

    final FetchBlockTask task = new FetchBlockAndBlobsSidecarTask(eth2P2PNetwork, blockRoot);

    assertThat(task.getBlockRoot()).isEqualTo(blockRoot);

    final Eth2Peer peer = registerNewPeer(1);

    when(peer.requestBlockAndBlobsSidecarByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new ResourceUnavailableException("oopsy")));

    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    final SafeFuture<FetchBlockResult> result = task.run();
    assertThat(result).isDone();
    final FetchBlockResult fetchBlockResult = result.getNow(null);
    assertThat(fetchBlockResult.isSuccessful()).isTrue();
    assertThat(fetchBlockResult.getBlock()).isEqualTo(block);
    assertThat(fetchBlockResult.getBlobsSidecar()).isEmpty();
  }
}
