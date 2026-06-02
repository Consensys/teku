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
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
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

public class GossipBeaconAggregateAndProofTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {

    final GossipBeaconAggregateAndProofMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBeaconAggregateAndProofMetaData.class);
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final BLSSignatureVerifier blsVerifier =
        signatureVerificationDisabled ? BLSSignatureVerifier.NOOP : BLSSignatureVerifier.SIMPLE;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
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

    // Track block roots that explicitly failed validation (marked failed: true in meta.yaml).
    // We load these blocks to obtain their hash tree root but do not import them, mirroring the
    // spec distinction between "block not seen" (IGNORE) and "block failed validation" (REJECT).
    final Set<Bytes32> failedBlockRoots = new HashSet<>();

    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipBeaconAggregateAndProofMetaData.BlockEntry blockEntry =
          blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (blockEntry.isFailed()) {
        // Record the root of this invalid block so aggregates voting for it are rejected.
        // Don't import it — a NOOP BLS verifier would accept it despite the bad signature.
        failedBlockRoots.add(block.getRoot());
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

    Optional<Checkpoint> customFinalizedCheckpoint = Optional.empty();
    if (metaData.getFinalizedCheckpoint() != null) {
      final GossipBeaconAggregateAndProofMetaData.FinalizedCheckpoint finalizedCheckpoint =
          metaData.getFinalizedCheckpoint();
      if (finalizedCheckpoint.getBlock() != null) {
        final Checkpoint checkpoint = finalizedCheckpoint.toCheckpoint(testDefinition, spec);
        final UpdatableStore.StoreTransaction tx = recentChainData.startStoreTransaction();
        tx.setFinalizedCheckpoint(checkpoint, false);
        safeJoin(tx.commit());
      } else {
        customFinalizedCheckpoint =
            Optional.of(finalizedCheckpoint.toCheckpoint(testDefinition, spec));
      }
    }

    final AttestationValidator attestationValidator =
        new AttestationValidator(
            spec,
            AsyncBLSSignatureVerifier.wrap(blsVerifier),
            createGossipValidationHelper(
                spec, recentChainData, metricsSystem, customFinalizedCheckpoint));
    final AggregateAttestationValidator aggregateValidator =
        new AggregateAttestationValidator(
            spec, attestationValidator, AsyncBLSSignatureVerifier.wrap(blsVerifier));

    for (final GossipBeaconAggregateAndProofMetaData.Message message : metaData.getMessages()) {
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      forkChoice.onTick(messageTimeMs, Optional.empty());

      final SignedAggregateAndProof signedAggregateAndProof =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              spec.getGenesisSchemaDefinitions().getSignedAggregateAndProofSchema());

      // Failed-block check: aggregate votes for a block that failed validation
      final Bytes32 votedBlockRoot =
          signedAggregateAndProof.getMessage().getAggregate().getData().getBeaconBlockRoot();
      if (failedBlockRoots.contains(votedBlockRoot)) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for aggregate %s voting for failed block %s",
                message.getMessage(), votedBlockRoot)
            .isEqualTo("reject");
        continue;
      }

      final ValidatableAttestation validatableAttestation =
          ValidatableAttestation.aggregateFromNetwork(spec, signedAggregateAndProof);
      final InternalValidationResult result =
          aggregateValidator.validate(validatableAttestation).join();

      switch (message.getExpected()) {
        case "valid" ->
            assertThat(result.code())
                .describedAs(
                    "Expected aggregate %s to be valid but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.ACCEPT);
        case "reject" ->
            assertThat(result.code())
                .describedAs(
                    "Expected aggregate %s to be rejected but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.REJECT);
        case "ignore" ->
            assertThat(result.code())
                .describedAs(
                    "Expected aggregate %s to be ignored but got %s: %s",
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
                  public boolean currentFinalizedCheckpointIsAncestorOfAttestationBlock(
                      final Bytes32 blockRoot) {
                    return spec.getAncestor(
                            getForkChoiceStrategy(),
                            blockRoot,
                            finalizedCheckpoint.getEpochStartSlot(spec))
                        .map(ancestorRoot -> ancestorRoot.equals(finalizedCheckpoint.getRoot()))
                        .orElse(false);
                  }
                })
        .orElseGet(() -> new GossipValidationHelper(spec, recentChainData, metricsSystem));
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipBeaconAggregateAndProofMetaData {

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

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
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
      GossipBeaconAggregateAndProofMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
