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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

@TestSpecContext(milestone = {ELECTRA, FULU})
public class DataColumnSidecarsByRootIntegrationTest extends AbstractRpcMethodIntegrationTest {

  private Eth2Peer peer;
  private SpecMilestone specMilestone;
  private Optional<DataColumnsByRootIdentifierSchema> dataColumnsIdentifierSchema =
      Optional.empty();

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    peer = createPeer(specContext.getSpec());
    specMilestone = specContext.getSpecMilestone();
    if (specMilestone.isGreaterThanOrEqualTo(FULU)) {
      dataColumnsIdentifierSchema =
          Optional.of(
              SchemaDefinitionsFulu.required(
                      specContext.getSpec().forMilestone(FULU).getSchemaDefinitions())
                  .getDataColumnsByRootIdentifierSchema());
    }
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldFailBeforeFuluMilestone() {
    assumeThat(specMilestone).isLessThan(FULU);
    assertThatThrownBy(() -> requestDataColumnSidecarsByRoot(peer, List.of()))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("DataColumnSidecarsByRoot method is not supported");
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldReturnEmptyDataColumnSidecarsAfterFuluMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(FULU);
    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRoot(
            peer,
            List.of(dataColumnsIdentifierSchema.orElseThrow().create(Bytes32.ZERO, UInt64.ZERO)));
    assertThat(dataColumnSidecars).isEmpty();
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldReturnDataColumnSidecarsOnFuluMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(FULU);

    // generate 2 blobs per block
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobs(true);
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(2));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    final List<UInt64> columns = List.of(UInt64.ZERO, UInt64.ONE);

    // grab expected data column sidecars from storage
    final List<DataColumnSidecar> expectedDataColumnSidecars =
        retrieveCanonicalDataColumnSidecarsFromPeerStorage(
            Stream.of(UInt64.ONE, UInt64.valueOf(3)), columns);

    // request all expected plus a non existing
    final List<DataColumnsByRootIdentifier> requestedDataColumnIds =
        Stream.concat(
                Stream.of(
                    dataColumnsIdentifierSchema.orElseThrow().create(Bytes32.ZERO, UInt64.ZERO)),
                expectedDataColumnSidecars.stream()
                    .map(
                        sidecar ->
                            dataColumnsIdentifierSchema
                                .orElseThrow()
                                .create(sidecar.getBeaconBlockRoot(), sidecar.getIndex())))
            .toList();

    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRoot(peer, requestedDataColumnIds);

    assertThat(dataColumnSidecars).containsExactlyInAnyOrderElementsOf(expectedDataColumnSidecars);
  }

  private List<DataColumnSidecar> requestDataColumnSidecarsByRoot(
      final Eth2Peer peer, final List<DataColumnsByRootIdentifier> dataColumnIdentifiers)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<DataColumnSidecar> dataColumnSidecars = new ArrayList<>();
    waitFor(
        peer.requestDataColumnSidecarsByRoot(
            dataColumnIdentifiers, RpcResponseListener.from(dataColumnSidecars::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return dataColumnSidecars;
  }
}
