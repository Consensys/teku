/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.servlet.AsyncContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.v1.BlockEvent;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.FinalizedCheckpointEvent;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.api.response.v1.SyncStateChangeEvent;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.sync.events.SyncState;

public class EventSubscriptionManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SpecConfig specConfig = spec.getGenesisSpecConfig();
  private final JsonProvider jsonProvider = new JsonProvider();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  protected final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  protected final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  protected final SyncDataProvider syncDataProvider = mock(SyncDataProvider.class);
  private final ConfigProvider configProvider = new ConfigProvider(spec);
  // chain reorg fields
  private final UInt64 slot = UInt64.valueOf("1024100");
  private final UInt64 epoch = spec.computeEpochAtSlot(slot);
  private final UInt64 depth = UInt64.valueOf(100);
  private final ChainReorgEvent chainReorgEvent =
      new ChainReorgEvent(
          slot,
          depth,
          data.randomBytes32(),
          data.randomBytes32(),
          data.randomBytes32(),
          data.randomBytes32(),
          epoch);

  private final HeadEvent headEvent =
      new HeadEvent(
          slot,
          data.randomBytes32(),
          data.randomBytes32(),
          false,
          data.randomBytes32(),
          data.randomBytes32());
  private final SignedContributionAndProof contributionAndProof =
      data.randomSignedContributionAndProof(0L);

  private final FinalizedCheckpointEvent sampleCheckpointEvent =
      new FinalizedCheckpointEvent(data.randomBytes32(), data.randomBytes32(), epoch);

  private final SyncState sampleSyncState = SyncState.IN_SYNC;
  private final SignedBeaconBlock sampleBlock =
      SignedBeaconBlock.create(data.randomSignedBeaconBlock(0));
  private final Attestation sampleAttestation = new Attestation(data.randomAttestation(0));
  private final SignedVoluntaryExit sampleVoluntaryExit =
      new SignedVoluntaryExit(data.randomSignedVoluntaryExit());

  private final AsyncContext async = mock(AsyncContext.class);
  private final EventChannels channels = mock(EventChannels.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse res = mock(HttpServletResponse.class);
  private final ServletResponse srvResponse = mock(ServletResponse.class);
  private final TestServletOutputStream outputStream = new TestServletOutputStream();
  private final Context ctx = new Context(req, res, Collections.emptyMap());
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private SseClient client1;

  private EventSubscriptionManager manager;

  @BeforeEach
  void setup() throws IOException {
    when(req.getAsyncContext()).thenReturn(async);
    when(async.getResponse()).thenReturn(srvResponse);
    when(srvResponse.getOutputStream()).thenReturn(outputStream);
    manager =
        new EventSubscriptionManager(
            nodeDataProvider,
            chainDataProvider,
            jsonProvider,
            syncDataProvider,
            configProvider,
            asyncRunner,
            channels,
            10);
    client1 = new SseClient(ctx);
  }

  @Test
  void shouldPropagateReorgMessages() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=chain_reorg");
    manager.registerClient(client1);

    triggerReorgEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: chain_reorg\n");
    final ChainReorgEvent event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), ChainReorgEvent.class);

    assertThat(event).isEqualTo(chainReorgEvent);
  }

  @Test
  void shouldPropagateHeadEvent() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerHeadEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: head\n");
    final HeadEvent event =
        jsonProvider.jsonToObject(eventString.substring(eventString.indexOf("{")), HeadEvent.class);

    assertThat(event).isEqualTo(headEvent);
  }

  @Test
  void shouldPropagateContributions() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=contribution_and_proof");
    manager.registerClient(client1);

    triggerContributionEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: contribution_and_proof\n");
  }

  @Test
  void shouldPropagateHeadAndReorg() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=chain_reorg,head");
    manager.registerClient(client1);

    triggerReorgEvent();
    final List<String> events = outputStream.getEvents();
    assertThat(events.get(0)).contains("event: chain_reorg\n");
    assertThat(events.get(1)).contains("event: head\n");
  }

  @Test
  void shouldPropagateMultipleMessagesIfSubscribed() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=chain_reorg,finalized_checkpoint");
    manager.registerClient(client1);

    triggerFinalizedCheckpointEvent();
    triggerReorgEvent();
    assertThat(outputStream.countEvents()).isEqualTo(2);
  }

  @Test
  void shouldPropagateFinalizedCheckpointMessages() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=finalized_checkpoint");
    manager.registerClient(client1);
    when(chainDataProvider.getStateRootFromBlockRoot(sampleCheckpointEvent.block))
        .thenReturn(Optional.of(sampleCheckpointEvent.state));

    triggerFinalizedCheckpointEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: finalized_checkpoint\n");
    final FinalizedCheckpointEvent event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), FinalizedCheckpointEvent.class);

    assertThat(event).isEqualTo(sampleCheckpointEvent);
  }

  @Test
  void shouldPropagateSyncState() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=sync_state");
    manager.registerClient(client1);

    triggerSyncStateEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: sync_state\n");
    final SyncStateChangeEvent event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), SyncStateChangeEvent.class);

    assertThat(event).isEqualTo(new SyncStateChangeEvent(sampleSyncState.name()));
  }

  @Test
  void shouldPropagateBlock() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=block");
    manager.registerClient(client1);

    triggerBlockEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: block\n");
    final BlockEvent event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), BlockEvent.class);

    assertThat(event)
        .isEqualTo(BlockEvent.fromSignedBeaconBlock(sampleBlock.asInternalSignedBeaconBlock(spec)));
  }

  @Test
  void shouldPropagateAttestation() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=attestation");
    manager.registerClient(client1);

    triggerAttestationEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: attestation\n");
    final Attestation event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), Attestation.class);

    assertThat(event).isEqualTo(sampleAttestation);
  }

  @Test
  void shouldPropagateVoluntaryExit() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=voluntary_exit");
    manager.registerClient(client1);

    triggerVoluntaryExitEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: voluntary_exit\n");
    final SignedVoluntaryExit event =
        jsonProvider.jsonToObject(
            eventString.substring(eventString.indexOf("{")), SignedVoluntaryExit.class);

    assertThat(event).isEqualTo(sampleVoluntaryExit);
  }

  @Test
  void shouldNotGetFinalizedCheckpointIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);
    triggerFinalizedCheckpointEvent();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldNotGetReorgIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=finalized_checkpoint");
    manager.registerClient(client1);

    triggerReorgEvent();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldNotGetHeadIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=finalized_checkpoint");
    manager.registerClient(client1);

    triggerHeadEvent();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldNotGetBlockIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerBlockEvent();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldNotGetAttestationIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerAttestationEvent();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  @Test
  void shouldNotGetVoluntaryExitIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerVoluntaryExitEvent();
    assertThat(outputStream.getWriteCounter()).isEqualTo(0);
  }

  private void triggerVoluntaryExitEvent() {
    manager.onNewVoluntaryExit(
        sampleVoluntaryExit.asInternalSignedVoluntaryExit(), InternalValidationResult.ACCEPT);
    asyncRunner.executeQueuedActions();
  }

  private void triggerAttestationEvent() {
    manager.onNewAttestation(
        ValidateableAttestation.from(spec, sampleAttestation.asInternalAttestation()));
    asyncRunner.executeQueuedActions();
  }

  private void triggerBlockEvent() {
    manager.onNewBlock(sampleBlock.asInternalSignedBeaconBlock(spec));
    asyncRunner.executeQueuedActions();
  }

  private void triggerSyncStateEvent() {
    manager.onSyncStateChange(sampleSyncState);
    asyncRunner.executeQueuedActions();
  }

  private void triggerFinalizedCheckpointEvent() {
    manager.onNewFinalizedCheckpoint(
        new Checkpoint(sampleCheckpointEvent.epoch, sampleCheckpointEvent.block));
    asyncRunner.executeQueuedActions();
  }

  private void triggerReorgEvent() {
    manager.chainHeadUpdated(
        chainReorgEvent.slot,
        chainReorgEvent.newHeadState,
        chainReorgEvent.newHeadBlock,
        chainReorgEvent.slot.mod(specConfig.getSlotsPerEpoch()).equals(UInt64.ZERO),
        headEvent.previousDutyDependentRoot,
        headEvent.currentDutyDependentRoot,
        Optional.of(
            new ReorgContext(
                chainReorgEvent.oldHeadBlock,
                UInt64.ZERO,
                chainReorgEvent.oldHeadState,
                chainReorgEvent.slot.minus(depth),
                Bytes32.ZERO)));
    asyncRunner.executeQueuedActions();
  }

  private void triggerHeadEvent() {
    manager.chainHeadUpdated(
        headEvent.slot,
        headEvent.state,
        headEvent.block,
        false,
        headEvent.previousDutyDependentRoot,
        headEvent.currentDutyDependentRoot,
        Optional.empty());
    asyncRunner.executeQueuedActions();
  }

  private void triggerContributionEvent() {
    manager.onSyncCommitteeContribution(contributionAndProof, InternalValidationResult.ACCEPT);
    asyncRunner.executeQueuedActions();
  }
}
