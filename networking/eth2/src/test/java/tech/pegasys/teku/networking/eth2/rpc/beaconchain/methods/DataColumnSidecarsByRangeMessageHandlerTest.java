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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@TestSpecContext(milestone = SpecMilestone.FULU)
public class DataColumnSidecarsByRangeMessageHandlerTest {

  private static final RequestKey ZERO_OBJECTS_REQUEST_APPROVAL = new RequestKey(ZERO, 101);
  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final NodeId nodeId = new MockNodeId(1);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<DataColumnSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());
  private final String protocolId =
      BeaconChainMethodIds.getDataColumnSidecarsByRangeMethodId(1, RPC_ENCODING);
  private final Optional<RequestKey> allowedObjectsRequest = Optional.of(new RequestKey(ZERO, 100));
  private DataStructureUtil dataStructureUtil;
  private DataColumnSidecarsByRangeMessageHandler handler;
  private DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema
      dataColumnSidecarsByRangeRequestMessageSchema;
  private UInt64 startSlot;
  private int slotsPerEpoch;
  private UInt64 maxRequestDataColumnSidecars;
  private final UInt64 count = UInt64.valueOf(5);
  private final List<UInt64> columnIndices = List.of(ZERO, ONE);

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    final Spec spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA ->
              throw new IllegalArgumentException("Milestone is not supported");
          case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
          case GLOAS -> TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
        };
    dataStructureUtil = new DataStructureUtil(spec);
    final SpecVersion specVersionFulu = spec.atEpoch(currentForkEpoch);
    final SpecConfigFulu specConfigFulu = SpecConfigFulu.required(specVersionFulu.getConfig());
    maxRequestDataColumnSidecars = UInt64.valueOf(specConfigFulu.getMaxRequestDataColumnSidecars());
    handler =
        new DataColumnSidecarsByRangeMessageHandler(
            SpecConfigFulu.required(specVersionFulu.getConfig()),
            metricsSystem,
            combinedChainDataClient,
            DasReqRespLogger.NOOP);
    final SchemaDefinitionsFulu schemaDefinitionsFulu =
        specVersionFulu.getSchemaDefinitions().toVersionFulu().orElseThrow();
    dataColumnSidecarsByRangeRequestMessageSchema =
        schemaDefinitionsFulu.getDataColumnSidecarsByRangeRequestMessageSchema();
    slotsPerEpoch = spec.getSlotsPerEpoch(ZERO);
    startSlot = currentForkEpoch.increment().times(slotsPerEpoch);

    when(peer.getId()).thenReturn(nodeId);
    when(peer.getDiscoveryNodeId()).thenReturn(Optional.of(dataStructureUtil.randomUInt256()));
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveDataColumnSidecarsRequest(any(), anyLong())).thenReturn(allowedObjectsRequest);

    // everything is finalized by default
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(startSlot.plus(count)));
    when(listener.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @TestTemplate
  public void validateRequest_validRequest() {
    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            dataColumnSidecarsByRangeRequestMessageSchema.create(startSlot, ONE, columnIndices));
    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenCountIsTooBig() {
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(
            startSlot, maxRequestDataColumnSidecars.increment(), columnIndices);
    final Optional<RpcException> result = handler.validateRequest(protocolId, request);
    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s data column sidecars can be requested per request",
                    maxRequestDataColumnSidecars)));
    final long countTooBigCount =
        metricsSystem.getCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requests_total",
            "count_too_big");
    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenCountOverflowsIntoNegativeNumber() {
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(
            // bypass ArithmeticException when calculating maxSlot
            startSlot, UInt64.MAX_VALUE.minus(startSlot), columnIndices);
    final Optional<RpcException> result = handler.validateRequest(protocolId, request);
    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s data column sidecars can be requested per request",
                    maxRequestDataColumnSidecars)));
    final long countTooBigCount =
        metricsSystem.getCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requests_total",
            "count_too_big");
    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenCountOverflowsIntoPositiveNumber() {
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(
            // this count will overflow into a positive number
            // ((Long.MAX_VALUE / 3) + 100) * 6 = 596
            startSlot,
            UInt64.valueOf((Long.MAX_VALUE / 3) + 100),
            UInt64.rangeClosed(ZERO, UInt64.valueOf(5)).toList());

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);
    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s data column sidecars can be requested per request",
                    maxRequestDataColumnSidecars)));
    final long countTooBigCount =
        metricsSystem.getCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requests_total",
            "count_too_big");
    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void shouldNotSendDataColumnSidecarsIfPeerIsRateLimited() {
    when(peer.approveDataColumnSidecarsRequest(any(), anyLong())).thenReturn(Optional.empty());
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(
            startSlot, count.times(maxRequestDataColumnSidecars), columnIndices);
    handler.onIncomingMessage(protocolId, peer, request, listener);
    // Requesting 5 * 2 * maxRequestDataColumnSidecars data column sidecars
    verify(peer)
        .approveDataColumnSidecarsRequest(
            any(),
            eq(count.times(maxRequestDataColumnSidecars).times(columnIndices.size()).longValue()));
    // No adjustment
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());
    final long rateLimitedCount =
        metricsSystem.getCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_data_column_sidecars_by_range_requests_total",
            "rate_limited");
    assertThat(rateLimitedCount).isOne();
    verifyNoInteractions(listener);
  }

  @TestTemplate
  public void shouldCompleteSuccessfullyIfNoDataColumnSidecarsInRange() {
    when(combinedChainDataClient.getDataColumnIdentifiers(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(
            currentForkEpoch.plus(1).times(slotsPerEpoch), count, columnIndices);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(peer)
        .approveDataColumnSidecarsRequest(any(), eq(count.times(columnIndices.size()).longValue()));
    verify(peer)
        .adjustDataColumnSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(0)));
    verify(combinedChainDataClient, never()).getDataColumnSidecars(any(), any());
    verify(listener, never()).respond(any());
    verify(listener).completeSuccessfully();
  }

  @TestTemplate
  public void shouldSendToPeerRequestedNumberOfFinalizedDataColumnSidecars() {
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(startSlot, count, columnIndices);
    final List<DataColumnSidecar> expectedSent =
        setUpDataColumnSidecarsData(startSlot, request.getMaxSlot(), columnIndices);
    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * 2 data column sidecars
    verify(peer)
        .approveDataColumnSidecarsRequest(any(), eq(count.times(columnIndices.size()).longValue()));
    // Sending expectedSent data column sidecars
    verify(peer)
        .adjustDataColumnSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(expectedSent.size())));
    final ArgumentCaptor<DataColumnSidecar> argumentCaptor =
        ArgumentCaptor.forClass(DataColumnSidecar.class);
    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());
    final List<DataColumnSidecar> actualSent = argumentCaptor.getAllValues();
    verify(listener).completeSuccessfully();
    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @TestTemplate
  public void shouldSendToPeerRequestedNumberOfCanonicalDataColumnSidecars() {

    final UInt64 latestFinalizedSlot = startSlot.plus(count).minus(3);
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(latestFinalizedSlot));

    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(startSlot, count, columnIndices);

    final List<DataColumnSidecar> allAvailableDataColumnSidecars =
        setUpDataColumnSidecarsData(startSlot, request.getMaxSlot(), columnIndices);

    // we simulate that the canonical non-finalized chain only contains data column sidecars from
    // last slotAndBlockRoot
    final SlotAndBlockRoot canonicalSlotAndBlockRoot =
        allAvailableDataColumnSidecars.getLast().getSlotAndBlockRoot();

    final List<DataColumnSidecar> expectedSent =
        allAvailableDataColumnSidecars.stream()
            .filter(
                dataColumnSidecar ->
                    dataColumnSidecar
                            .getSlot()
                            .isLessThanOrEqualTo(latestFinalizedSlot) // include finalized
                        || dataColumnSidecar
                            .getSlotAndBlockRoot()
                            .equals(canonicalSlotAndBlockRoot) // include non finalized
                )
            .toList();

    // let return only canonical slot and block root as canonical
    when(combinedChainDataClient.getAncestorRoots(eq(startSlot), eq(ONE), any()))
        .thenReturn(
            ImmutableSortedMap.of(
                canonicalSlotAndBlockRoot.getSlot(), canonicalSlotAndBlockRoot.getBlockRoot()));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * 2 data column sidecars
    verify(peer)
        .approveDataColumnSidecarsRequest(any(), eq(count.times(columnIndices.size()).longValue()));
    // Sending expectedSent data column sidecars
    verify(peer)
        .adjustDataColumnSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<DataColumnSidecar> argumentCaptor =
        ArgumentCaptor.forClass(DataColumnSidecar.class);

    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<DataColumnSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @TestTemplate
  public void shouldSendToPeerRequestedSingleSlotDataColumnSidecars() {
    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(startSlot, ONE, columnIndices);
    final List<DataColumnSidecar> expectedSent =
        setUpDataColumnSidecarsData(startSlot, request.getMaxSlot(), columnIndices);
    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 2 data column sidecars
    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(columnIndices.size())));
    // Sending expectedSent data column sidecars
    verify(peer)
        .adjustDataColumnSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(expectedSent.size())));
    final ArgumentCaptor<DataColumnSidecar> argumentCaptor =
        ArgumentCaptor.forClass(DataColumnSidecar.class);
    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());
    final List<DataColumnSidecar> actualSent = argumentCaptor.getAllValues();
    verify(listener).completeSuccessfully();
    assertThat(actualSent.size()).isOne();
    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @TestTemplate
  public void shouldIgnoreRequestWhenCountIsZero() {

    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(startSlot, ZERO, columnIndices);

    when(peer.approveDataColumnSidecarsRequest(any(), eq(0)))
        .thenReturn(Optional.of(ZERO_OBJECTS_REQUEST_APPROVAL));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(0)));
    // no adjustment
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());

    final ArgumentCaptor<DataColumnSidecar> argumentCaptor =
        ArgumentCaptor.forClass(DataColumnSidecar.class);

    verify(listener, never()).respond(argumentCaptor.capture());

    final List<DataColumnSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).isEmpty();
  }

  @TestTemplate
  public void shouldIgnoreRequestWhenCountIsZeroAndHotSlotRequested() {
    // not finalized
    final UInt64 hotStartSlot = startSlot.plus(7);

    final DataColumnSidecarsByRangeRequestMessage request =
        dataColumnSidecarsByRangeRequestMessageSchema.create(hotStartSlot, ZERO, columnIndices);

    when(peer.approveDataColumnSidecarsRequest(any(), eq(0)))
        .thenReturn(Optional.of(ZERO_OBJECTS_REQUEST_APPROVAL));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(peer).approveDataColumnSidecarsRequest(any(), eq(Long.valueOf(0)));
    // no adjustment
    verify(peer, never()).adjustDataColumnSidecarsRequest(any(), anyLong());

    final ArgumentCaptor<DataColumnSidecar> argumentCaptor =
        ArgumentCaptor.forClass(DataColumnSidecar.class);

    verify(listener, never()).respond(argumentCaptor.capture());

    final List<DataColumnSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).isEmpty();
  }

  private List<DataColumnSidecar> setUpDataColumnSidecarsData(
      final UInt64 startSlot, final UInt64 maxSlot, final List<UInt64> columns) {
    final List<Pair<SignedBeaconBlockHeader, DataColumnSlotAndIdentifier>>
        headersAndDataColumnSlotAndIdentifiers =
            setupDataColumnSidecarIdentifiers(startSlot, maxSlot, columns);
    when(combinedChainDataClient.getDataColumnIdentifiers(
            eq(startSlot), eq(maxSlot), any(UInt64.class)))
        .thenAnswer(
            args ->
                SafeFuture.completedFuture(
                    headersAndDataColumnSlotAndIdentifiers
                        .subList(
                            0,
                            Math.min(
                                headersAndDataColumnSlotAndIdentifiers.size(),
                                Math.toIntExact(args.getArgument(2, UInt64.class).longValue())))
                        .stream()
                        .map(Pair::getValue)
                        .toList()));
    return headersAndDataColumnSlotAndIdentifiers.stream()
        .map(this::setUpDataColumnSidecarDataForKey)
        .collect(Collectors.toList());
  }

  private List<Pair<SignedBeaconBlockHeader, DataColumnSlotAndIdentifier>>
      setupDataColumnSidecarIdentifiers(
          final UInt64 startSlot, final UInt64 maxSlot, final List<UInt64> columnIndices) {
    final List<Pair<SignedBeaconBlockHeader, DataColumnSlotAndIdentifier>>
        headersAndDataColumnSlotAndIdentifiers = new ArrayList<>();
    UInt64.rangeClosed(startSlot, maxSlot)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
              UInt64.rangeClosed(
                      ZERO, dataStructureUtil.randomUInt64(columnIndices.size()).minusMinZero(1))
                  .forEach(
                      columnIndex -> {
                        headersAndDataColumnSlotAndIdentifiers.add(
                            Pair.of(
                                block.asHeader(),
                                new DataColumnSlotAndIdentifier(
                                    slot, block.getRoot(), columnIndex)));
                      });
            });
    return headersAndDataColumnSlotAndIdentifiers;
  }

  private DataColumnSidecar setUpDataColumnSidecarDataForKey(
      final Pair<SignedBeaconBlockHeader, DataColumnSlotAndIdentifier>
          headerAndSataColumnSlotAndIdentifier) {
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(
            headerAndSataColumnSlotAndIdentifier.getKey(),
            headerAndSataColumnSlotAndIdentifier.getValue().columnIndex());
    when(combinedChainDataClient.getSidecar(headerAndSataColumnSlotAndIdentifier.getValue()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataColumnSidecar)));
    return dataColumnSidecar;
  }
}
