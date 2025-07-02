/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestApproval;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifierSchema;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarByRootCustody;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(milestone = {SpecMilestone.FULU})
public class DataColumnSidecarsByRootMessageHandlerTest {

  private final UInt64 genesisTime = UInt64.valueOf(1982239L);
  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private DataColumnSidecarsByRootRequestMessageSchema messageSchema;
  private DataColumnsByRootIdentifierSchema identifierSchema;
  private final ArgumentCaptor<DataColumnSidecar> datacolumnSidecarCaptor =
      ArgumentCaptor.forClass(DataColumnSidecar.class);
  private final ArgumentCaptor<RpcException> rpcExceptionCaptor =
      ArgumentCaptor.forClass(RpcException.class);
  private final Optional<RequestApproval> allowedObjectsRequest =
      Optional.of(
          new RequestApproval.RequestApprovalBuilder().objectsCount(100).timeSeconds(ZERO).build());

  @SuppressWarnings("unchecked")
  private final ResponseCallback<DataColumnSidecar> callback = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final NodeId nodeId = new MockNodeId(1);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final DataColumnSidecarByRootCustody custody = mock(DataColumnSidecarByRootCustody.class);
  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);
  private String protocolId;
  private UInt64 currentForkFirstSlot;
  private DataStructureUtil dataStructureUtil;
  private DataColumnSidecarsByRootMessageHandler handler;
  private SpecMilestone specMilestone;
  private Spec spec;

  @BeforeEach
  public void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    specMilestone = specContext.getSpecMilestone();
    spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA ->
              throw new IllegalArgumentException("Milestone is not supported");
          case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
        };
    dataStructureUtil = new DataStructureUtil(spec);
    final SchemaDefinitionsFulu schemaDefinitionsFulu =
        spec.atEpoch(currentForkEpoch).getSchemaDefinitions().toVersionFulu().orElseThrow();
    messageSchema = schemaDefinitionsFulu.getDataColumnSidecarsByRootRequestMessageSchema();
    identifierSchema = schemaDefinitionsFulu.getDataColumnsByRootIdentifierSchema();
    currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    final RpcEncoding rpcEncoding =
        RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
    protocolId = BeaconChainMethodIds.getDataColumnSidecarsByRootMethodId(1, rpcEncoding);
    handler =
        new DataColumnSidecarsByRootMessageHandler(
            spec,
            metricsSystem,
            combinedChainDataClient,
            custody,
            custodyGroupCountManager,
            DasReqRespLogger.NOOP);

    when(peer.getId()).thenReturn(nodeId);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(dataStructureUtil.randomUInt256()));

    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveDataColumnSidecarsRequest(any(), anyLong())).thenReturn(allowedObjectsRequest);
    reset(combinedChainDataClient);
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot))));
    // fulu fork epoch is finalized
    when(combinedChainDataClient.getFinalizedBlock())
        .thenReturn(Optional.of(dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot)));
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);

    // mock store
    when(store.getGenesisTime()).thenReturn(genesisTime);
    // current epoch is 2
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(
                currentForkEpoch.increment().times(spec.getSlotsPerEpoch(ZERO)), genesisTime));

    // custodying everything by default
    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(IntStream.of(0, 128).mapToObj(UInt64::valueOf).toList());
  }

  @TestTemplate
  public void validateRequest_shouldNotAllowRequestLargerThanMaximumAllowed() {
    final int maxRequestDataColumnSidecars =
        SpecConfigFulu.required(spec.forMilestone(specMilestone).getConfig())
            .getMaxRequestDataColumnSidecars();
    when(recentChainData.getCurrentEpoch())
        .thenReturn(Optional.of(dataStructureUtil.randomEpoch()));

    final DataColumnSidecarsByRootRequestMessage request =
        messageSchema.of(
            generateDataColumnsByRootIdentifiers(maxRequestDataColumnSidecars / 2 + 1, 2));

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValueSatisfying(
            rpcException -> {
              assertThat(rpcException.getResponseCode()).isEqualTo(INVALID_REQUEST_CODE);
              assertThat(rpcException.getErrorMessageString())
                  .isEqualTo(
                      "Only a maximum of %d data column sidecars can be requested per request",
                      maxRequestDataColumnSidecars);
            });

    final long countTooBigCount =
        metricsSystem.getCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requests_total",
            "count_too_big");

    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void shouldNotSendDataColumnSidecarsIfPeerIsRateLimited() {

    when(peer.approveDataColumnSidecarsRequest(any(), anyLong())).thenReturn(Optional.empty());

    final DataColumnSidecarsByRootRequestMessage request =
        messageSchema.of(generateDataColumnsByRootIdentifiers(3, 2));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    // Requesting 6 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(6)));
    // No adjustment
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());

    final long rateLimitedCount =
        metricsSystem.getCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_root_requests_total",
            "rate_limited");

    assertThat(rateLimitedCount).isOne();

    verifyNoInteractions(callback);
  }

  @TestTemplate
  public void shouldSendAvailableOnlyResources() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 4).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();

    // the second block root can't be found in the database
    final Bytes32 secondBlockRoot = dataColumnsByRootIdentifiers[1].getBlockRoot();
    when(combinedChainDataClient.getBlockByBlockRoot(secondBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    when(custody.getCustodyDataColumnSidecarByRoot(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnIdentifier dataColumnIdentifier = invocation.getArgument(0);
              if (dataColumnIdentifier.blockRoot().equals(secondBlockRoot)) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              for (int i = 0; i < 4; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(dataColumnIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 3 data column sidecars
    verify(peer)
        .adjustDataColumnSidecarsRequest(eq(allowedObjectsRequest.get()), eq(Long.valueOf(3)));

    verify(combinedChainDataClient, never()).getNonCanonicalSidecar(any());
    verify(callback, times(3)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots =
        List.of(
            generatedSidecars.get(0).getBlockRoot(),
            generatedSidecars.get(2).getBlockRoot(),
            generatedSidecars.get(3).getBlockRoot());

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);
  }

  @TestTemplate
  public void
      shouldSendResourceUnavailableIfBlockRootReferencesBlockEarlierThanTheMinimumRequestEpoch() {
    // 1 million epoch
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(
                UInt64.valueOf(1_000_000).times(spec.getSlotsPerEpoch(ZERO)), genesisTime));

    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);

    // an old block out of availability window
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(UInt64.valueOf(100));
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBeaconBlock)));
    when(combinedChainDataClient.getNonCanonicalSidecar(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    when(custody.getCustodyDataColumnSidecarByRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Request cancelled due to error
    verify(peer, times(1))
        .adjustDataColumnSidecarsRequest(eq(allowedObjectsRequest.get()), eq(Long.valueOf(0)));

    verify(callback, never()).respond(any());
    verify(callback).completeWithErrorResponse(rpcExceptionCaptor.capture());

    final RpcException rpcException = rpcExceptionCaptor.getValue();

    assertThat(rpcException.getResponseCode()).isEqualTo(INVALID_REQUEST_CODE);
    assertThat(rpcException.getErrorMessageString())
        .isEqualTo(
            "Block root (%s) references a block earlier than the minimum_request_epoch",
            dataColumnsByRootIdentifiers[0].getBlockRoot());
  }

  @TestTemplate
  public void shouldSendCustodyOnlyResources() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        new DataColumnsByRootIdentifier[4];
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiersFirst =
        generateDataColumnsByRootIdentifiers(3, 1);
    dataColumnsByRootIdentifiers[0] = dataColumnsByRootIdentifiersFirst[0];
    dataColumnsByRootIdentifiers[1] = dataColumnsByRootIdentifiersFirst[1];
    dataColumnsByRootIdentifiers[2] = dataColumnsByRootIdentifiersFirst[2];
    // not in custody
    dataColumnsByRootIdentifiers[3] =
        identifierSchema.create(dataStructureUtil.randomBytes32(), ONE);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 4).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();
    when(custodyGroupCountManager.getCustodyColumnIndices())
        .thenReturn(List.of(dataColumnsByRootIdentifiers[0].getColumns().get(0)));

    when(custody.getCustodyDataColumnSidecarByRoot(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnIdentifier dataColumnIdentifier = invocation.getArgument(0);
              // it will not reach this step
              assertThat(dataColumnIdentifier.blockRoot())
                  .isNotEqualTo(dataColumnsByRootIdentifiers[3].getBlockRoot());
              for (int i = 0; i < 3; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(dataColumnIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              // not requesting #3
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 3 data column sidecars
    verify(peer)
        .adjustDataColumnSidecarsRequest(eq(allowedObjectsRequest.get()), eq(Long.valueOf(3)));

    verify(combinedChainDataClient, never()).getNonCanonicalSidecar(any());
    verify(callback, times(3)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots =
        List.of(
            generatedSidecars.get(0).getBlockRoot(),
            generatedSidecars.get(1).getBlockRoot(),
            generatedSidecars.get(2).getBlockRoot());

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);
  }

  @TestTemplate
  public void shouldSendToPeerRequestedBlobSidecars() {
    final DataColumnsByRootIdentifier[] dataColumnsByRootIdentifiers =
        generateDataColumnsByRootIdentifiers(4, 1);
    final List<DataColumnSidecar> generatedSidecars =
        IntStream.range(0, 4).mapToObj(__ -> dataStructureUtil.randomDataColumnSidecar()).toList();

    when(custody.getCustodyDataColumnSidecarByRoot(any()))
        .thenAnswer(
            invocation -> {
              final DataColumnIdentifier dataColumnIdentifier = invocation.getArgument(0);
              for (int i = 0; i < 4; ++i) {
                if (dataColumnsByRootIdentifiers[i]
                    .getBlockRoot()
                    .equals(dataColumnIdentifier.blockRoot())) {
                  return SafeFuture.completedFuture(Optional.of(generatedSidecars.get(i)));
                }
              }
              throw new RuntimeException("Should never get here");
            });

    handler.onIncomingMessage(
        protocolId, peer, messageSchema.of(dataColumnsByRootIdentifiers), callback);

    // Requesting 4 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(4)));
    // Sending 3 data column sidecars
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());

    verify(combinedChainDataClient, never()).getNonCanonicalSidecar(any());
    verify(callback, times(4)).respond(datacolumnSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedDataColumnSidecarBlockRoots =
        datacolumnSidecarCaptor.getAllValues().stream()
            .map(DataColumnSidecar::getBlockRoot)
            .toList();
    final List<Bytes32> expectedDataColumnIdentifiersBlockRoots =
        List.of(
            generatedSidecars.get(0).getBlockRoot(),
            generatedSidecars.get(1).getBlockRoot(),
            generatedSidecars.get(2).getBlockRoot(),
            generatedSidecars.get(3).getBlockRoot());

    assertThat(respondedDataColumnSidecarBlockRoots)
        .containsExactlyElementsOf(expectedDataColumnIdentifiersBlockRoots);
  }

  private DataColumnsByRootIdentifier[] generateDataColumnsByRootIdentifiers(
      final int numberOfBlocks, final int columnIndicesPerBlock) {
    return IntStream.range(0, numberOfBlocks)
        .mapToObj(
            __ ->
                identifierSchema.create(
                    dataStructureUtil.randomBytes32(),
                    IntStream.range(0, columnIndicesPerBlock).mapToObj(UInt64::valueOf).toList()))
        .toArray(DataColumnsByRootIdentifier[]::new);
  }
}
