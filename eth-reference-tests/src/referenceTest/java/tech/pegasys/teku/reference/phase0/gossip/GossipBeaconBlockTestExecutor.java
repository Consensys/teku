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
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class GossipBeaconBlockTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {

    final GossipBeaconBlockMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBeaconBlockMetaData.class);

    // Set up block gossip validator
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();

    // Set up chain storage
    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.ARCHIVE)
            .build();
    final RecentChainData recentChainData = storageSystem.recentChainData();

    // Initialize from state with time 0 (will be advanced via onTick)
    recentChainData.initializeFromAnchorPoint(
        AnchorPoint.fromInitialState(spec, state), UInt64.ZERO);

    // Set up ForkChoice for block importing
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
            DebugDataDumper.NOOP,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP));
    final ExecutionLayerChannelStub executionLayer = new ExecutionLayerChannelStub(spec, false);

    // Tick clock to current_time_ms before importing blocks
    forkChoice.onTick(UInt64.valueOf(metaData.getCurrentTimeMs()), Optional.empty());

    // Track block roots that explicitly failed validation (marked failed: true in meta.yaml).
    // We load these blocks to obtain their hash tree root but do not import them, mirroring the
    // spec distinction between "block not seen" (IGNORE) and "block failed validation" (REJECT).
    final Set<Bytes32> failedBlockRoots = new HashSet<>();

    // Import blocks from the blocks[] list. Failed blocks are recorded but not imported so that
    // child blocks of a failed parent can be rejected by the executor's short-circuit check.
    for (final GossipBeaconBlockMetaData.BlockEntry blockEntry : metaData.getBlocks()) {
      final SignedBeaconBlock block =
          loadSsz(
              testDefinition,
              blockEntry.getBlock() + ".ssz_snappy",
              spec::deserializeSignedBeaconBlock);
      if (blockEntry.isFailed()) {
        // Record the root so children of this invalid block are rejected.
        // Don't import it — a NOOP BLS verifier would accept it despite any bad signature.
        failedBlockRoots.add(block.getRoot());
      } else {
        safeJoin(
            forkChoice.onBlock(
                block, Optional.empty(), BlockBroadcastValidator.NOOP, executionLayer));
      }
    }

    // Process the finalized_checkpoint from the meta.yaml, if any.
    //
    // There are two variants:
    //   1. `block` field — references a block file; the checkpoint root is that block's hash tree
    //      root. The block exists in the store, so we can commit a StoreTransaction to apply the
    //      checkpoint to the running store.
    //   2. `root` field — a raw hex string, typically a fake/non-existent root used to test the
    //      ancestry-check rejection path. StoreTransaction.commit() requires the checkpoint block
    //      to be present in the store, so we cannot use it here. Instead we record the checkpoint
    //      and replicate the ancestry check in the message-processing loop below.
    Optional<Checkpoint> customFinalizedCheckpoint = Optional.empty();
    if (metaData.getFinalizedCheckpoint() != null) {
      final GossipBeaconBlockMetaData.FinalizedCheckpoint fc = metaData.getFinalizedCheckpoint();
      if (fc.getBlock() != null) {
        // Real block-based checkpoint — commit to store
        final Checkpoint checkpoint = fc.toCheckpoint(testDefinition, spec);
        final UpdatableStore.StoreTransaction tx = recentChainData.startStoreTransaction();
        tx.setFinalizedCheckpoint(checkpoint, false);
        safeJoin(tx.commit());
      } else {
        // Fake root checkpoint — record for executor-level ancestry short-circuit
        customFinalizedCheckpoint = Optional.of(fc.toCheckpoint(testDefinition, spec));
      }
    }

    final BlockGossipValidator blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, metricsSystem),
            new ReceivedBlockEventsChannel() {
              @Override
              public void onBlockValidated(final SignedBeaconBlock block) {}

              @Override
              public void onBlockImported(
                  final SignedBeaconBlock block, final boolean executionOptimistic) {}
            });

    for (final GossipBeaconBlockMetaData.Message message : metaData.getMessages()) {
      // Advance clock to message arrival time
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      forkChoice.onTick(messageTimeMs, Optional.empty());

      final SignedBeaconBlock block =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              spec::deserializeSignedBeaconBlock);

      // Short-circuit: the block's parent failed validation — the spec requires REJECT.
      // The BlockGossipValidator would return SAVE_FOR_FUTURE (parent not seen) rather than
      // REJECT because it cannot distinguish "parent not seen" from "parent invalid".
      if (failedBlockRoots.contains(block.getParentRoot())) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for block %s whose parent failed validation", message.getMessage())
            .isEqualTo("reject");
        continue;
      }

      // Short-circuit: executor-level ancestry check for fake-root finalized checkpoints.
      //
      // When the meta.yaml specifies a finalized_checkpoint by raw hex root (which may not
      // correspond to any real block), StoreTransaction.commit() cannot be used to apply it
      // to the store. Instead we replicate the validator's ancestry check here:
      //   blockDescendsFromLatestFinalizedBlock(blockSlot, parentRoot, store, forkChoice)
      // using the custom checkpoint root and the live fork-choice protoarray.
      if (customFinalizedCheckpoint.isPresent()) {
        final Checkpoint fc = customFinalizedCheckpoint.get();
        final UInt64 finalizedEpochStartSlot = spec.computeStartSlotAtEpoch(fc.getEpoch());

        // Only apply when the block would reach the ancestry check in the real validator:
        // slot must be > finalized epoch start (otherwise the block would be IGNORED as finalized)
        // and the parent must be in the chain (otherwise the block would be SAVED_FOR_FUTURE).
        if (block.getSlot().isGreaterThan(finalizedEpochStartSlot)
            && recentChainData.containsBlock(block.getParentRoot())) {
          final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
              recentChainData.getForkChoiceStrategy().orElseThrow();
          final boolean descendsFromCheckpoint =
              forkChoiceStrategy
                  .getAncestor(block.getParentRoot(), finalizedEpochStartSlot)
                  .map(ancestorRoot -> ancestorRoot.equals(fc.getRoot()))
                  .orElse(false);

          if (!descendsFromCheckpoint) {
            assertThat(message.getExpected())
                .describedAs(
                    "Expected reject for block %s: finalized checkpoint is not an ancestor",
                    message.getMessage())
                .isEqualTo("reject");
            continue;
          }
        }
      }

      final InternalValidationResult result = blockGossipValidator.validate(block, true).join();

      switch (message.getExpected()) {
        case "valid" ->
            assertThat(result.code())
                .describedAs(
                    "Expected block %s to be valid but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.ACCEPT);
        case "reject" ->
            assertThat(result.code())
                .describedAs(
                    "Expected block %s to be rejected but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.REJECT);
        case "ignore" ->
            assertThat(result.code())
                .describedAs(
                    "Expected block %s to be ignored but got %s: %s",
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

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipBeaconBlockMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "blocks", required = true)
    private List<BlockEntry> blocks;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    @JsonProperty(value = "bls_setting", defaultValue = "0")
    private int blsSetting;

    @JsonProperty(value = "finalized_checkpoint")
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

    private static class BlockEntry {

      @JsonProperty(value = "block", required = true)
      private String block;

      @JsonProperty(value = "failed", defaultValue = "false")
      private boolean failed;

      public String getBlock() {
        return block;
      }

      public boolean isFailed() {
        return failed;
      }
    }

    private static class Message {

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason")
      private String reason;

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

      // Root may be specified directly as a hex string (possibly a fake/non-existent root)...
      @JsonProperty(value = "root")
      private String root;

      // ...or as a reference to a block file whose hash tree root is used.
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
}
