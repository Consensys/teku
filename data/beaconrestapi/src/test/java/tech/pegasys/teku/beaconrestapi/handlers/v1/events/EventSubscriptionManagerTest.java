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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.PayloadAttributesEvent.Data;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.PayloadAttributesEvent.PayloadAttributes;
import tech.pegasys.teku.beaconrestapi.handlers.v1.events.PayloadAttributesEvent.PayloadAttributesData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.ReorgContext;

public class EventSubscriptionManagerTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final SpecConfig specConfig = spec.getGenesisSpecConfig();
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
          epoch,
          false);

  private final HeadEvent headEvent =
      new HeadEvent(
          slot,
          data.randomBytes32(),
          data.randomBytes32(),
          false,
          true,
          data.randomBytes32(),
          data.randomBytes32());
  private final SignedContributionAndProof contributionAndProof =
      data.randomSignedContributionAndProof(0L);

  private final FinalizedCheckpointEvent sampleCheckpointEvent =
      new FinalizedCheckpointEvent(data.randomBytes32(), data.randomBytes32(), epoch, false);

  private final SyncState sampleSyncState = SyncState.IN_SYNC;
  private final SignedBeaconBlock sampleBlock = data.randomSignedBeaconBlock(0);
  private final BlobSidecar sampleBlobSidecar = data.randomBlobSidecar();
  private final DataColumnSidecar sampleDataColumnSidecar = data.randomDataColumnSidecar();
  private final Attestation sampleAttestation = data.randomAttestation(0);
  private final SingleAttestation singleAttestation = data.randomSingleAttestation();

  private final AttesterSlashing sampleAttesterSlashing =
      spec.getGenesisSchemaDefinitions()
          .getAttesterSlashingSchema()
          .create(data.randomIndexedAttestation(), data.randomIndexedAttestation());

  private final ProposerSlashing sampleProposerSlashing =
      new ProposerSlashing(
          data.randomSignedBeaconBlockHeader(), data.randomSignedBeaconBlockHeader());
  private final SignedVoluntaryExit sampleVoluntaryExit = data.randomSignedVoluntaryExit();
  private final SignedBlsToExecutionChange sampleBlsToExecutionChange =
      data.randomSignedBlsToExecutionChange();
  final PayloadBuildingAttributes samplePayloadAttributes =
      data.randomPayloadBuildingAttributes(true);
  final PayloadAttributesData samplePayloadAttributesData =
      new PayloadAttributesData(
          SpecMilestone.FULU,
          new Data(
              samplePayloadAttributes.getProposalSlot(),
              samplePayloadAttributes.getParentBeaconBlockRoot(),
              data.randomUInt64(),
              data.randomBytes32(),
              samplePayloadAttributes.getProposerIndex(),
              new PayloadAttributes(
                  samplePayloadAttributes.getTimestamp(),
                  samplePayloadAttributes.getPrevRandao(),
                  samplePayloadAttributes.getFeeRecipient(),
                  samplePayloadAttributes.getWithdrawals(),
                  Optional.of(samplePayloadAttributes.getParentBeaconBlockRoot()))));
  final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification =
      new ForkChoiceUpdatedResultNotification(
          new ForkChoiceState(
              data.randomBytes32(),
              data.randomSlot(),
              samplePayloadAttributesData.data().parentExecutionBlockNumber(),
              samplePayloadAttributesData.data().parentExecutionBlockHash(),
              data.randomBytes32(),
              data.randomBytes32(),
              false),
          Optional.of(samplePayloadAttributes),
          false,
          new SafeFuture<>());

  private final AsyncContext async = mock(AsyncContext.class);
  private final EventChannels channels = mock(EventChannels.class);
  private final HttpServletRequest req = mock(HttpServletRequest.class);
  private final HttpServletResponse res = mock(HttpServletResponse.class);
  private final TestServletOutputStream outputStream = new TestServletOutputStream();
  private final Context ctx = new StubContext(req, res);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private SseClient client1;

  private EventSubscriptionManager manager;

  @BeforeEach
  void setup() throws IOException {
    when(req.getAsyncContext()).thenReturn(async);
    when(async.getResponse()).thenReturn(res);
    when(res.getOutputStream()).thenReturn(outputStream);
    manager =
        new EventSubscriptionManager(
            spec,
            nodeDataProvider,
            chainDataProvider,
            syncDataProvider,
            configProvider,
            asyncRunner,
            channels,
            StubTimeProvider.withTimeInMillis(1000),
            10);
    client1 = new SseClient(ctx);
  }

  @Test
  void shouldPropagateReorgMessages() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=chain_reorg");
    manager.registerClient(client1);

    triggerReorgEvent();
    checkEvent("chain_reorg", chainReorgEvent);
  }

  @Test
  void shouldPropagateHeadEvent() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerHeadEvent();
    checkEvent("head", headEvent);
  }

  @Test
  void shouldPropagateContributions() {
    when(req.getQueryString()).thenReturn("&topics=contribution_and_proof");
    manager.registerClient(client1);

    triggerContributionEvent();
    final String eventString = outputStream.getString();
    assertThat(eventString).contains("event: contribution_and_proof\n");
  }

  @Test
  void shouldPropagateHeadAndReorg() {
    when(req.getQueryString()).thenReturn("&topics=chain_reorg,head");
    manager.registerClient(client1);

    triggerReorgEvent();
    final List<String> events = outputStream.getEvents();
    assertThat(events.get(0)).contains("event: chain_reorg\n");
    assertThat(events.get(1)).contains("event: head\n");
  }

  @Test
  void shouldPropagateMultipleMessagesIfSubscribed() {
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
    when(chainDataProvider.getStateRootFromBlockRoot(sampleCheckpointEvent.getData().block))
        .thenReturn(Optional.of(sampleCheckpointEvent.getData().state));

    triggerFinalizedCheckpointEvent();
    checkEvent("finalized_checkpoint", sampleCheckpointEvent);
  }

  @Test
  void shouldPropagateSyncState() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=sync_state");
    manager.registerClient(client1);

    triggerSyncStateEvent();
    checkEvent("sync_state", new SyncStateChangeEvent(sampleSyncState.name()));
  }

  @Test
  void shouldPropagateBlock() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=block");
    manager.registerClient(client1);

    triggerBlockEvent();
    checkEvent("block", new BlockEvent(sampleBlock, false));
  }

  @Test
  void shouldPropagateBlockGossip() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=block_gossip");
    manager.registerClient(client1);

    triggerBlockGossipEvent();
    checkEvent("block_gossip", new BlockGossipEvent(sampleBlock));
  }

  @Test
  void shouldPropagateBlobSidecar() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=blob_sidecar");
    manager.registerClient(client1);

    triggerBlobSidecarEvent();
    checkEvent("blob_sidecar", BlobSidecarEvent.create(spec, sampleBlobSidecar));
  }

  @Test
  void shouldPropagateDataColumnSidecar() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=data_column_sidecar");
    manager.registerClient(client1);

    triggerDataColumnSidecarEvent();

    checkEvent("data_column_sidecar", DataColumnSidecarEvent.create(sampleDataColumnSidecar));
  }

  @Test
  void shouldPropagatePayloadAttributes() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=payload_attributes");
    manager.registerClient(client1);

    triggerPayloadAttributesEvent();
    checkEvent(
        "payload_attributes",
        PayloadAttributesEvent.create(
            samplePayloadAttributesData.milestone(),
            samplePayloadAttributes,
            forkChoiceUpdatedResultNotification.forkChoiceState()));
  }

  @Test
  void shouldPropagateAttestation() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=attestation");
    manager.registerClient(client1);

    triggerAttestationEvent();
    checkEvent("attestation", new AttestationEvent(sampleAttestation));
  }

  @Test
  void shouldPropagateSingleAttestation() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=single_attestation");
    manager.registerClient(client1);

    triggerSingleAttestationEvent();
    checkEvent("single_attestation", new SingleAttestationEvent(singleAttestation));
  }

  @Test
  void shouldPropagateAttesterSlashing() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=attester_slashing");
    manager.registerClient(client1);
    triggerAttesterSlashingEvent();
    checkEvent("attester_slashing", new AttesterSlashingEvent(sampleAttesterSlashing));
  }

  @Test
  void shouldNotGetAttesterSlashingIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerAttesterSlashingEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldPropagateProposerSlashing() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=proposer_slashing");
    manager.registerClient(client1);
    triggerProposerSlashingEvent();
    checkEvent("proposer_slashing", new ProposerSlashingEvent(sampleProposerSlashing));
  }

  @Test
  void shouldNotGetProposerSlashingIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerProposerSlashingEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldPropagateVoluntaryExit() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=voluntary_exit");
    manager.registerClient(client1);

    triggerVoluntaryExitEvent();
    checkEvent("voluntary_exit", new VoluntaryExitEvent(sampleVoluntaryExit));
  }

  @Test
  void shouldNotGetFinalizedCheckpointIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);
    triggerFinalizedCheckpointEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldNotGetReorgIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=finalized_checkpoint");
    manager.registerClient(client1);

    triggerReorgEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldNotGetHeadIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=finalized_checkpoint");
    manager.registerClient(client1);

    triggerHeadEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldNotGetBlockIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerBlockEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldNotGetBlobSidecarIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerBlobSidecarEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldNotGetAttestationIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerAttestationEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldNotGetVoluntaryExitIfNotSubscribed() {
    when(req.getQueryString()).thenReturn("&topics=head");
    manager.registerClient(client1);

    triggerVoluntaryExitEvent();
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  @Test
  void shouldPropagateBlsToExecutionChanges() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=bls_to_execution_change");
    manager.registerClient(client1);

    triggerBlsToExecutionChangeEvent(InternalValidationResult.ACCEPT);
    checkEvent(
        "bls_to_execution_change", new BlsToExecutionChangeEvent(sampleBlsToExecutionChange));
  }

  @Test
  void shouldNotPropagateInvalidBlsToExecutionChanges() throws IOException {
    when(req.getQueryString()).thenReturn("&topics=bls_to_execution_change");
    manager.registerClient(client1);

    triggerBlsToExecutionChangeEvent(InternalValidationResult.reject("invalid"));
    assertThat(outputStream.countEvents()).isEqualTo(0);
  }

  private void triggerVoluntaryExitEvent() {
    manager.onNewVoluntaryExit(sampleVoluntaryExit, InternalValidationResult.ACCEPT, false);
    asyncRunner.executeQueuedActions();
  }

  private void triggerBlsToExecutionChangeEvent(final InternalValidationResult validationResult) {
    manager.onNewBlsToExecutionChange(sampleBlsToExecutionChange, validationResult, false);
    asyncRunner.executeQueuedActions();
  }

  private void triggerAttestationEvent() {
    manager.onNewAttestation(ValidatableAttestation.from(spec, sampleAttestation));
    asyncRunner.executeQueuedActions();
  }

  private void triggerSingleAttestationEvent() {
    manager.onNewAttestation(ValidatableAttestation.from(spec, singleAttestation));
    asyncRunner.executeQueuedActions();
  }

  private void triggerAttesterSlashingEvent() {
    manager.onNewAttesterSlashing(sampleAttesterSlashing, InternalValidationResult.ACCEPT, false);
    asyncRunner.executeQueuedActions();
  }

  private void triggerProposerSlashingEvent() {
    manager.onNewProposerSlashing(sampleProposerSlashing, InternalValidationResult.ACCEPT, false);
    asyncRunner.executeQueuedActions();
  }

  private void triggerBlockEvent() {
    manager.onNewBlock(sampleBlock, false);
    asyncRunner.executeQueuedActions();
  }

  private void triggerBlockGossipEvent() {
    manager.onNewBlockGossip(sampleBlock);
    asyncRunner.executeQueuedActions();
  }

  private void triggerBlobSidecarEvent() {
    manager.onNewBlobSidecar(sampleBlobSidecar);
    asyncRunner.executeQueuedActions();
  }

  private void triggerDataColumnSidecarEvent() {
    manager.onNewDataColumnSidecar(sampleDataColumnSidecar);
    asyncRunner.executeQueuedActions();
  }

  private void triggerPayloadAttributesEvent() {
    manager.onForkChoiceUpdatedResult(forkChoiceUpdatedResultNotification);
    asyncRunner.executeQueuedActions();
  }

  private void triggerSyncStateEvent() {
    manager.onSyncStateChange(sampleSyncState);
    asyncRunner.executeQueuedActions();
  }

  private void triggerFinalizedCheckpointEvent() {
    manager.onNewFinalizedCheckpoint(
        new Checkpoint(
            sampleCheckpointEvent.getData().epoch, sampleCheckpointEvent.getData().block),
        false);
    asyncRunner.executeQueuedActions();
  }

  private void triggerReorgEvent() {
    manager.chainHeadUpdated(
        chainReorgEvent.getData().getSlot(),
        chainReorgEvent.getData().getNewHeadState(),
        chainReorgEvent.getData().getNewHeadBlock(),
        chainReorgEvent.getData().getSlot().mod(specConfig.getSlotsPerEpoch()).equals(UInt64.ZERO),
        false,
        headEvent.getData().getPreviousDutyDependentRoot(),
        headEvent.getData().getCurrentDutyDependentRoot(),
        Optional.of(
            new ReorgContext(
                chainReorgEvent.getData().getOldHeadBlock(),
                UInt64.ZERO,
                chainReorgEvent.getData().getOldHeadState(),
                chainReorgEvent.getData().getSlot().minus(depth),
                Bytes32.ZERO)));
    asyncRunner.executeQueuedActions();
  }

  private void triggerHeadEvent() {
    manager.chainHeadUpdated(
        headEvent.getData().getSlot(),
        headEvent.getData().getState(),
        headEvent.getData().getBlock(),
        false,
        true,
        headEvent.getData().getPreviousDutyDependentRoot(),
        headEvent.getData().getCurrentDutyDependentRoot(),
        Optional.empty());
    asyncRunner.executeQueuedActions();
  }

  private void triggerContributionEvent() {
    manager.onSyncCommitteeContribution(
        contributionAndProof, InternalValidationResult.ACCEPT, false);
    asyncRunner.executeQueuedActions();
  }

  private <T, E extends Event<T>> void checkEvent(final String eventType, final E event)
      throws JsonProcessingException {
    final String eventString = outputStream.getString();
    assertThat(eventString).contains(String.format("event: %s\n", eventType));

    final String expected =
        eventString.substring(eventString.indexOf("{"), eventString.lastIndexOf("}") + 1);
    final String result = JsonUtil.serialize(event.getData(), event.getJsonTypeDefinition());
    assertThat(result).isEqualTo(expected);
  }
}
