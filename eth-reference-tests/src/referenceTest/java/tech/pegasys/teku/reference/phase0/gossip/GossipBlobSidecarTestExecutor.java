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

package tech.pegasys.teku.reference.phase0.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.reference.TestDataUtils.createAnchorFromStateAndMatchingBlock;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.gossip.BlobSidecarGossipManager.TopicSubnetIdAwareOperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.KzgRetriever;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class GossipBlobSidecarTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {

    final GossipBlobSidecarMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBlobSidecarMetaData.class);
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final BlobSidecarSchema blobSidecarSchema =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions()).getBlobSidecarSchema();

    final List<BlockEntryAndBlock> blocks =
        metaData.getBlocks().stream()
            .map(
                blockEntry ->
                    new BlockEntryAndBlock(
                        blockEntry,
                        loadSsz(
                            testDefinition,
                            blockEntry.getBlock() + ".ssz_snappy",
                            spec::deserializeSignedBeaconBlock)))
            .toList();
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();

    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.ARCHIVE)
            .build();
    final RecentChainData recentChainData = storageSystem.recentChainData();

    final AnchorPoint anchorPoint =
        createAnchorFromStateAndMatchingBlock(
            spec,
            state,
            blocks.stream()
                .filter(blockEntryAndBlock -> !blockEntryAndBlock.blockEntry().isFailed())
                .map(BlockEntryAndBlock::block)
                .toList());
    recentChainData.initializeFromAnchorPoint(anchorPoint, UInt64.ZERO);

    final InlineEventThread eventThread = new InlineEventThread();
    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData);
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            new NoopForkChoiceNotifier(),
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            true,
            LateBlockReorgPreparationHandler.NOOP,
            DebugDataDumper.NOOP,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP));
    final ExecutionLayerChannelStub executionLayer = new ExecutionLayerChannelStub(spec, false);

    forkChoice.onTick(UInt64.valueOf(metaData.getCurrentTimeMs()), Optional.empty());

    final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();

    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipBlobSidecarMetaData.BlockEntry blockEntry = blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (block.getRoot().equals(anchorPoint.getRoot())) {
        continue;
      }
      final BlockImportResult importResult =
          safeJoin(
              forkChoice.onBlock(
                  block, Optional.empty(), BlockBroadcastValidator.NOOP, executionLayer));
      if (blockEntry.isFailed()) {
        // The block has been seen (so it is "available" with a known slot), but is recorded as
        // invalid so blob sidecars whose parent is this block are rejected rather than deferred.
        invalidBlockRoots.put(
            block.getRoot(), BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
      } else {
        assertThat(importResult.isSuccessful())
            .describedAs("Expected setup block %s to import successfully", blockEntry.getBlock())
            .isTrue();
      }
    }

    Optional<Checkpoint> customFinalizedCheckpoint = Optional.empty();
    if (metaData.getFinalizedCheckpoint() != null) {
      final GossipBlobSidecarMetaData.FinalizedCheckpoint finalizedCheckpoint =
          metaData.getFinalizedCheckpoint();
      if (finalizedCheckpoint.getBlock() != null) {
        final Checkpoint checkpoint = finalizedCheckpoint.toCheckpoint(testDefinition, spec);
        final UpdatableStore.StoreTransaction tx = recentChainData.startStoreTransaction();
        tx.setFinalizedCheckpoint(checkpoint, false);
        safeJoin(tx.commit());
      } else {
        // Some generated gossip tests specify a finalized checkpoint as a raw root that is not
        // backed by a block in the store. StoreTransaction.commit() requires the checkpoint block
        // to be present, so keep the checkpoint aside and model the validator's ancestry check in a
        // test-only GossipValidationHelper below.
        customFinalizedCheckpoint =
            Optional.of(finalizedCheckpoint.toCheckpoint(testDefinition, spec));
      }
    }

    final GossipValidationHelper gossipValidationHelper =
        createGossipValidationHelper(
            spec, recentChainData, metricsSystem, customFinalizedCheckpoint);
    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
    // The test spec ships with a NoOpKZG that accepts every proof. Only the invalid-kzg-proof case
    // needs the kzg proof rule actually exercised, so load a real trusted setup there. Other cases
    // keep the NoOpKZG default since their blobs are not necessarily kzg-valid.
    if (testDefinition.getTestName().contains("reject_invalid_kzg_proof")) {
      final KZG kzg =
          KzgRetriever.getKzgWithLoadedTrustedSetup(spec, testDefinition.getConfigName());
      miscHelpersDeneb.setKzg(kzg);
    }
    final BlobSidecarGossipValidator blobSidecarValidator =
        BlobSidecarGossipValidator.create(
            spec, invalidBlockRoots, gossipValidationHelper, miscHelpersDeneb);
    // Adapt the validator to the gossip OperationProcessor SPI so the subnet rule can be exercised
    // with the exact production component
    // (BlobSidecarGossipManager.TopicSubnetIdAwareOperationProcessor),
    // which rejects on a topic/subnet mismatch before delegating to the validator. The validator
    // itself does not own the subnet rule (in production it is enforced per gossip topic).
    final OperationProcessor<BlobSidecar> validatorProcessor =
        (blobSidecar, arrivalTimestamp) -> blobSidecarValidator.validate(blobSidecar);

    for (final GossipBlobSidecarMetaData.Message message : metaData.getMessages()) {
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      forkChoice.onTick(messageTimeMs, Optional.empty());

      final BlobSidecar blobSidecar =
          loadSsz(testDefinition, message.getMessage() + ".ssz_snappy", blobSidecarSchema);

      final OperationProcessor<BlobSidecar> processor =
          message.getSubnetId() == null
              ? validatorProcessor
              : new TopicSubnetIdAwareOperationProcessor(
                  spec, message.getSubnetId(), validatorProcessor);
      final InternalValidationResult result =
          safeJoin(processor.process(blobSidecar, Optional.empty()));

      switch (message.getExpected()) {
        case "valid" ->
            assertThat(result.code())
                .describedAs(
                    "Expected blob sidecar %s to be valid but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.ACCEPT);
        case "reject" ->
            assertThat(result.code())
                .describedAs(
                    "Expected blob sidecar %s to be rejected but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.REJECT);
        case "ignore" ->
            assertThat(result.code())
                .describedAs(
                    "Expected blob sidecar %s to be ignored but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isIn(ValidationResultCode.IGNORE, ValidationResultCode.SAVE_FOR_FUTURE);
        default ->
            throw new AssertionError(
                "Unexpected expected value: "
                    + message.getExpected()
                    + " for message: "
                    + message.getMessage());
      }
    }
  }

  private static GossipValidationHelper createGossipValidationHelper(
      final Spec spec,
      final RecentChainData recentChainData,
      final StubMetricsSystem metricsSystem,
      final Optional<Checkpoint> finalizedCheckpointOverride) {
    return finalizedCheckpointOverride
        .<GossipValidationHelper>map(
            finalizedCheckpoint ->
                new GossipValidationHelper(spec, recentChainData, metricsSystem) {
                  @Override
                  public boolean currentFinalizedCheckpointIsAncestorOfBlock(
                      final UInt64 blockSlot, final Bytes32 blockParentRoot) {
                    // The production helper reads the finalized checkpoint from Store, but ref-test
                    // gives a fake checkpoint that cannot be committed there given that it doesn't
                    // ahve a block root.
                    // We preserve the production ancestry rule while substituting the fixture's
                    // checkpoint root.
                    if (blockSlot.isLessThanOrEqualTo(
                        finalizedCheckpoint.getEpochStartSlot(spec))) {
                      return false;
                    }
                    return spec.getAncestor(
                            getForkChoiceStrategy(),
                            blockParentRoot,
                            finalizedCheckpoint.getEpochStartSlot(spec))
                        .map(ancestorRoot -> ancestorRoot.equals(finalizedCheckpoint.getRoot()))
                        .orElse(false);
                  }
                })
        .orElseGet(() -> new GossipValidationHelper(spec, recentChainData, metricsSystem));
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipBlobSidecarMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "blocks", required = true)
    private List<BlockEntry> blocks;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    @JsonProperty(value = "finalized_checkpoint", required = false)
    private FinalizedCheckpoint finalizedCheckpoint;

    public List<BlockEntry> getBlocks() {
      return blocks;
    }

    public List<Message> getMessages() {
      return messages;
    }

    public long getCurrentTimeMs() {
      return currentTimeMs;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }

    public FinalizedCheckpoint getFinalizedCheckpoint() {
      return finalizedCheckpoint;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BlockEntry {

      @JsonProperty(value = "block", required = true)
      private String block;

      @JsonProperty(value = "failed", required = false, defaultValue = "false")
      private boolean failed;

      public String getBlock() {
        return block;
      }

      public boolean isFailed() {
        return failed;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {

      @JsonProperty(value = "subnet_id", required = false)
      private Integer subnetId;

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public Integer getSubnetId() {
        return subnetId;
      }

      public long getOffsetMs() {
        return offsetMs;
      }

      public String getMessage() {
        return message;
      }

      public String getExpected() {
        return expected;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FinalizedCheckpoint {

      @JsonProperty(value = "epoch", required = true)
      private long epoch;

      @JsonProperty(value = "root")
      private String root;

      @JsonProperty(value = "block")
      private String block;

      public String getBlock() {
        return block;
      }

      public Checkpoint toCheckpoint(final TestDefinition testDefinition, final Spec spec) {
        final Bytes32 checkpointRoot;
        if (root != null) {
          checkpointRoot = Bytes32.fromHexString(root);
        } else if (block != null) {
          final SignedBeaconBlock signedBlock =
              loadSsz(testDefinition, block + ".ssz_snappy", spec::deserializeSignedBeaconBlock);
          checkpointRoot = signedBlock.getRoot();
        } else {
          throw new IllegalStateException(
              "finalized_checkpoint must specify either 'root' or 'block'");
        }
        return new Checkpoint(UInt64.valueOf(epoch), checkpointRoot);
      }
    }
  }

  private record BlockEntryAndBlock(
      GossipBlobSidecarMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
