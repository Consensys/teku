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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.KzgRetriever;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
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

public class GossipBlobSidecarTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipBlobSidecarMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBlobSidecarMetaData.class);

    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final KZG kzg = KzgRetriever.getKzgWithLoadedTrustedSetup(spec, testDefinition.getConfigName());
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);

    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
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
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            new NoopForkChoiceNotifier(),
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            new MergeTransitionBlockValidator(spec, recentChainData),
            true,
            LateBlockReorgPreparationHandler.NOOP,
            DebugDataDumper.NOOP,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP));
    final ExecutionLayerChannelStub executionLayer = new ExecutionLayerChannelStub(spec, false);

    forkChoice.onTick(UInt64.valueOf(metaData.getCurrentTimeMs()), Optional.empty());

    final Set<Bytes32> failedBlockRoots = new HashSet<>();
    final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();
    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipBlobSidecarMetaData.BlockEntry blockEntry = blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (blockEntry.isFailed()) {
        failedBlockRoots.add(block.getRoot());
        invalidBlockRoots.put(block.getRoot(), BlockImportResult.FAILED_INVALID_ANCESTRY);
      } else if (!block.getRoot().equals(anchorPoint.getRoot())) {
        final BlockImportResult importResult =
            safeJoin(
                forkChoice.onBlock(
                    block, Optional.empty(), BlockBroadcastValidator.NOOP, executionLayer));
        assertThat(importResult.isSuccessful())
            .describedAs("Expected setup block %s to import successfully", blockEntry.getBlock())
            .isTrue();
      }
    }

    Optional<Checkpoint> finalizedCheckpointOverride = Optional.empty();
    if (metaData.getFinalizedCheckpoint() != null) {
      finalizedCheckpointOverride =
          Optional.of(metaData.getFinalizedCheckpoint().toCheckpoint(testDefinition, spec));
    }

    final BlobSidecarGossipValidator blobSidecarGossipValidator =
        BlobSidecarGossipValidator.create(
            spec,
            invalidBlockRoots,
            new GossipValidationHelper(spec, recentChainData, metricsSystem),
            MiscHelpersDeneb.required(spec.getGenesisSpec().miscHelpers()));

    final SchemaDefinitionsDeneb schemaDefinitions =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions());

    for (final GossipBlobSidecarMetaData.Message message : metaData.getMessages()) {
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      forkChoice.onTick(messageTimeMs, Optional.empty());

      final BlobSidecar blobSidecar =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              schemaDefinitions.getBlobSidecarSchema());

      if (!spec.computeSubnetForBlobSidecar(blobSidecar)
          .equals(UInt64.valueOf(message.getSubnetId()))) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for blob sidecar %s on wrong subnet %s",
                message.getMessage(), message.getSubnetId())
            .isEqualTo("reject");
        continue;
      }

      final BeaconBlockHeader blockHeader = blobSidecar.getSignedBeaconBlockHeader().getMessage();
      if (finalizedCheckpointOverride.isPresent()
          && blockHeader
              .getSlot()
              .isLessThanOrEqualTo(finalizedCheckpointOverride.get().getEpochStartSlot(spec))) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected ignore for blob sidecar %s at or before finalized slot",
                message.getMessage())
            .isEqualTo("ignore");
        continue;
      }

      if (failedBlockRoots.contains(blockHeader.getParentRoot())) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for blob sidecar %s whose parent failed validation",
                message.getMessage())
            .isEqualTo("reject");
        continue;
      }

      if (finalizedCheckpointOverride.isPresent()
          && !blockDescendsFromCustomFinalizedCheckpoint(
              spec, recentChainData, blockHeader, finalizedCheckpointOverride.get())) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for blob sidecar %s: finalized checkpoint is not an ancestor",
                message.getMessage())
            .isEqualTo("reject");
        continue;
      }

      final InternalValidationResult result =
          blobSidecarGossipValidator.validate(blobSidecar).join();
      assertExpectedResult(message, result);
    }
  }

  private static boolean blockDescendsFromCustomFinalizedCheckpoint(
      final Spec spec,
      final RecentChainData recentChainData,
      final BeaconBlockHeader blockHeader,
      final Checkpoint finalizedCheckpoint) {
    final UInt64 finalizedEpochStartSlot =
        spec.computeStartSlotAtEpoch(finalizedCheckpoint.getEpoch());
    if (!blockHeader.getSlot().isGreaterThan(finalizedEpochStartSlot)
        || !recentChainData.containsBlock(blockHeader.getParentRoot())) {
      return true;
    }

    final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getForkChoiceStrategy().orElseThrow();
    return forkChoiceStrategy
        .getAncestor(blockHeader.getParentRoot(), finalizedEpochStartSlot)
        .map(ancestorRoot -> ancestorRoot.equals(finalizedCheckpoint.getRoot()))
        .orElse(false);
  }

  private static void assertExpectedResult(
      final GossipBlobSidecarMetaData.Message message, final InternalValidationResult result) {
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

      @JsonProperty(value = "subnet_id", required = true)
      private int subnetId;

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public int getSubnetId() {
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

      @JsonProperty(value = "root", required = false)
      private String root;

      @JsonProperty(value = "block", required = false)
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
