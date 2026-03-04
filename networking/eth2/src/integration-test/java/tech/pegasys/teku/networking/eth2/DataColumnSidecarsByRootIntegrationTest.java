/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;

public abstract class DataColumnSidecarsByRootIntegrationTest
    extends AbstractRpcMethodIntegrationTest {

  private Eth2Peer peer;
  private DataColumnsByRootIdentifierSchema dataColumnsIdentifierSchema;

  protected abstract Spec createSpec();

  protected abstract DataColumnsByRootIdentifierSchema createDataColumnsIdentifierSchema(Spec spec);

  @BeforeEach
  public void setUp() {
    final Spec spec = createSpec();
    peer = createPeer(spec);
    dataColumnsIdentifierSchema = createDataColumnsIdentifierSchema(spec);
  }

  @Test
  public void requestDataColumnSidecars_shouldReturnEmptyDataColumnSidecars()
      throws ExecutionException, InterruptedException, TimeoutException {
    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRoot(
            peer, List.of(dataColumnsIdentifierSchema.create(Bytes32.ZERO, UInt64.ZERO)));
    assertThat(dataColumnSidecars).isEmpty();
  }

  @Test
  public void requestDataColumnSidecars_shouldReturnDataColumnSidecars()
      throws ExecutionException, InterruptedException, TimeoutException {
    // generate 2 blobs per block
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobs(true);
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(2));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    // save canonical blocks to local storage, simulating local node having received the canonical
    // beacon blocks via gossip/sync before requesting data column sidecars
    peerStorage
        .chainBuilder()
        .streamBlocksAndStates()
        .forEach(blockAndState -> localPeerStorage.chainUpdater().saveBlock(blockAndState));

    final List<UInt64> columns = List.of(UInt64.ZERO, UInt64.ONE);

    // grab expected data column sidecars from storage
    final List<DataColumnSidecar> expectedDataColumnSidecars =
        retrieveCanonicalDataColumnSidecarsFromPeerStorage(
            Stream.of(UInt64.ONE, UInt64.valueOf(3)), columns);
    assertThat(expectedDataColumnSidecars).isNotEmpty();

    // request all expected plus a non existing
    final List<DataColumnsByRootIdentifier> requestedDataColumnIds =
        Stream.concat(
                Stream.of(dataColumnsIdentifierSchema.create(Bytes32.ZERO, UInt64.ZERO)),
                expectedDataColumnSidecars.stream()
                    .map(
                        sidecar ->
                            dataColumnsIdentifierSchema.create(
                                sidecar.getBeaconBlockRoot(), sidecar.getIndex())))
            .toList();

    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRoot(peer, requestedDataColumnIds);

    assertThat(dataColumnSidecars).containsExactlyInAnyOrderElementsOf(expectedDataColumnSidecars);
  }
}
