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

package tech.pegasys.teku.beacon.sync.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {GLOAS})
public class HistoricalBatchFetcherGloasTest {

  private final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Map<Bytes32, SignedBlindedExecutionPayloadEnvelope>>
      blindedExecutionPayloadsCaptor = ArgumentCaptor.forClass(Map.class);

  private final int maxRequests = 5;

  private Spec spec;
  private ChainBuilder chainBuilder;
  private StorageSystem storageSystem;
  private CombinedChainDataClient chainDataClient;
  private List<SignedBeaconBlock> blockBatch;
  private Map<Bytes32, SignedBlindedExecutionPayloadEnvelope> expectedBlindedPayloads;
  private SignedBeaconBlock firstBlockInBatch;
  private SignedBeaconBlock lastBlockInBatch;
  private HistoricalBatchFetcher fetcher;
  private RespondingEth2Peer peer;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec);
    storageSystem = InMemoryStorageSystemBuilder.create().specProvider(spec).build();
    storageSystem.chainUpdater().initializeGenesis();

    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
    when(storageUpdateChannel.onFinalizedBlocks(any(), any(), any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(20);

    blockBatch =
        chainBuilder
            .streamBlocksAndStates(10, 20)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    lastBlockInBatch = chainBuilder.getLatestBlockAndState().getBlock();
    firstBlockInBatch = blockBatch.getFirst();

    final SchemaDefinitionsGloas schemaDefinitionsGloas =
        SchemaDefinitionsGloas.required(
            spec.atSlot(lastBlockInBatch.getSlot()).getSchemaDefinitions());
    expectedBlindedPayloads =
        chainBuilder
            .streamExecutionPayloads(10, 20)
            .collect(
                Collectors.toMap(
                    SignedExecutionPayloadEnvelope::getBeaconBlockRoot,
                    envelope -> envelope.blind(schemaDefinitionsGloas)));

    chainDataClient =
        new CombinedChainDataClient(
            storageSystem.recentChainData(),
            mock(StorageQueryChannel.class),
            spec,
            LateBlockReorgPreparationHandler.NOOP,
            false);

    peer = RespondingEth2Peer.create(spec, chainBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            maxRequests);

    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true));
  }

  @TestTemplate
  public void run_returnAllBlocksAndExecutionPayloadEnvelopesOnFirstRequest() {
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(2);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(), any(), blindedExecutionPayloadsCaptor.capture(), any());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
    assertThat(blindedExecutionPayloadsCaptor.getValue()).isEqualTo(expectedBlindedPayloads);
  }

  @TestTemplate
  public void run_failsOnInvalidExecutionPayloadEnvelopeSignature() {
    // block signatures pass on the first verify call but envelope signatures fail on the second
    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true))
        .thenReturn(SafeFuture.completedFuture(false));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<BeaconBlockSummary> future = fetcher.run();
    peer.completePendingRequests();

    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withMessageContaining("Batch execution payload envelope signature verification failed");
  }
}
