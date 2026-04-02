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

package tech.pegasys.teku.reference.fulu.networking;

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
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class GossipBeaconAttestationTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipBeaconAttestationMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBeaconAttestationMetaData.class);
    final Spec spec = testDefinition.getSpec();
    final BeaconState genesisState = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();

    // Set up chain storage
    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.ARCHIVE)
            .build();
    final RecentChainData recentChainData = storageSystem.recentChainData();

    // Initialize from genesis state with time 0 (will be advanced via onTick)
    recentChainData.initializeFromGenesis(genesisState, UInt64.ZERO);

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

    // When a custom finalized_checkpoint is set, its root will not be in the chain, so any block
    // import attempt would fail. Skip all imports so that the attestation validator's
    // block-not-available path produces the expected IGNORE result.
    final boolean hasCustomFinalizedCheckpoint = metaData.getFinalizedCheckpoint() != null;
    if (!hasCustomFinalizedCheckpoint) {
      for (final GossipBeaconAttestationMetaData.BlockEntry blockEntry : metaData.getBlocks()) {
        final SignedBeaconBlock block =
            loadSsz(
                testDefinition,
                blockEntry.getBlock() + ".ssz_snappy",
                spec::deserializeSignedBeaconBlock);
        if (blockEntry.isFailed()) {
          // Record the root of this invalid block so attestations voting for it are rejected.
          // Don't import it — a NOOP BLS verifier would accept it despite the bad signature.
          failedBlockRoots.add(block.getRoot());
        } else {
          safeJoin(
              forkChoice.onBlock(
                  block, Optional.empty(), BlockBroadcastValidator.NOOP, executionLayer));
        }
      }
    }

    // Set up attestation validator
    final BLSSignatureVerifier blsVerifier =
        metaData.getBlsSetting() == BlsSetting.REQUIRED
            ? BLSSignatureVerifier.SIMPLE
            : BLSSignatureVerifier.NOOP;
    final AttestationValidator attestationValidator =
        new AttestationValidator(
            spec,
            AsyncBLSSignatureVerifier.wrap(blsVerifier),
            new GossipValidationHelper(spec, recentChainData, metricsSystem));

    // Track seen attestations (by hash tree root) for "already seen" duplicate detection
    final Set<Bytes32> seenAttestationRoots = new HashSet<>();

    for (final GossipBeaconAttestationMetaData.Message message : metaData.getMessages()) {
      // Advance clock to message arrival time
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      forkChoice.onTick(messageTimeMs, Optional.empty());

      final Attestation attestation =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              spec.getGenesisSchemaDefinitions().getAttestationSchema());
      final Bytes32 attestationRoot = attestation.hashTreeRoot();

      // Already-seen check: same attestation from same validator seen before
      if (seenAttestationRoots.contains(attestationRoot)) {
        assertThat(message.getExpected())
            .describedAs("Expected ignore for already-seen attestation %s", message.getMessage())
            .isEqualTo("ignore");
        continue;
      }

      // Failed-block check: attestation votes for a block that failed validation
      final Bytes32 votedBlockRoot = attestation.getData().getBeaconBlockRoot();
      if (failedBlockRoots.contains(votedBlockRoot)) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for attestation %s voting for failed block %s",
                message.getMessage(), votedBlockRoot)
            .isEqualTo("reject");
        continue;
      }

      final ValidatableAttestation validatableAttestation =
          ValidatableAttestation.fromNetwork(spec, attestation, message.getSubnetId());
      final InternalValidationResult result =
          attestationValidator.validate(validatableAttestation).join();

      switch (message.getExpected()) {
        case "valid" -> {
          assertThat(result.code())
              .describedAs(
                  "Expected attestation %s to be valid but got %s: %s",
                  message.getMessage(), result.code(), result.getDescription().orElse(""))
              .isEqualTo(ValidationResultCode.ACCEPT);
          seenAttestationRoots.add(attestationRoot);
        }
        case "reject" ->
            assertThat(result.code())
                .describedAs(
                    "Expected attestation %s to be rejected but got %s: %s",
                    message.getMessage(), result.code(), result.getDescription().orElse(""))
                .isEqualTo(ValidationResultCode.REJECT);
        case "ignore" ->
            assertThat(result.code())
                .describedAs(
                    "Expected attestation %s to be ignored but got %s: %s",
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
  private static class GossipBeaconAttestationMetaData {

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
    private Object finalizedCheckpoint;

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

    public Object getFinalizedCheckpoint() {
      return finalizedCheckpoint;
    }

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
  }
}
