/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.reference.phase0.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.api.Condition;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.PandaPrinter;
import tech.pegasys.teku.statetransition.forkchoice.StubForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceTestExecutor implements TestExecutor {
  private static final Logger LOG = LogManager.getLogger();
  public static final ImmutableMap<String, TestExecutor> FORK_CHOICE_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("fork_choice/get_head", new ForkChoiceTestExecutor())
          .put("fork_choice/ex_ante", new ForkChoiceTestExecutor())
          .put("fork_choice/reorg", new ForkChoiceTestExecutor())
          .put("fork_choice/on_block", new ForkChoiceTestExecutor())
          .put("fork_choice/on_merge_block", new ForkChoiceTestExecutor())
          .put("sync/optimistic", new ForkChoiceTestExecutor())
          .build();

  private final List<?> testsToSkip;

  public ForkChoiceTestExecutor(String... testsToSkip) {
    this.testsToSkip = List.of(testsToSkip);
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    if (testsToSkip.contains(testDefinition.getTestName())) {
      throw new TestAbortedException(
          "Test " + testDefinition.getDisplayName() + " has been ignored");
    }

    // Note: The fork choice spec says there may be settings in a meta.yaml file but currently no
    // tests actually have one, so we currently don't bother trying to load it.
    final BeaconState anchorState =
        TestDataUtils.loadStateFromSsz(testDefinition, "anchor_state.ssz_snappy");
    final Spec spec = testDefinition.getSpec();
    final SignedBeaconBlock anchorBlock = loadAnchorBlock(testDefinition);

    final StorageSystem storageSystem =
        InMemoryStorageSystemBuilder.create().specProvider(spec).build();
    final RecentChainData recentChainData = storageSystem.recentChainData();
    recentChainData.initializeFromAnchorPoint(
        AnchorPoint.fromInitialBlockAndState(
            spec, new SignedBlockAndState(anchorBlock, anchorState)),
        spec.getSlotStartTime(anchorBlock.getSlot(), anchorState.getGenesisTime()));

    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData, ExecutionLayerChannel.NOOP);
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            recentChainData,
            new StubForkChoiceNotifier(),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            PandaPrinter.NOOP,
            true,
            true,
            DEFAULT_FORK_CHOICE_UPDATE_HEAD_ON_BLOCK_IMPORT_ENABLED);
    final ExecutionLayerChannelStub executionLayer =
        new ExecutionLayerChannelStub(spec, false, Optional.empty());

    try {
      runSteps(testDefinition, spec, recentChainData, forkChoice, executionLayer);
    } catch (final AssertionError e) {
      final String protoArrayData =
          recentChainData.getForkChoiceStrategy().orElseThrow().getNodeData().stream()
              .map(Object::toString)
              .collect(Collectors.joining("\n"));
      throw new AssertionError(
          e.getMessage()
              + "\nJustified checkpoint: "
              + recentChainData.getJustifiedCheckpoint().orElse(null)
              + "\nFinalized checkpoint: "
              + recentChainData.getFinalizedCheckpoint().orElse(null)
              + "\nProtoarray data:\n"
              + protoArrayData,
          e);
    }
  }

  /**
   * The anchor block is currently always a Phase 0 block because of the way the specs repo are
   * doing Altair genesis. See https://github.com/ethereum/eth2.0-specs/pull/2323
   *
   * @param testDefinition the test definition
   * @return the anchor block for the test
   */
  private SignedBeaconBlock loadAnchorBlock(final TestDefinition testDefinition) {
    final BeaconBlock anchorBlock =
        TestDataUtils.loadSsz(
            testDefinition,
            "anchor_block.ssz_snappy",
            testDefinition.getSpec()::deserializeBeaconBlock);
    return SignedBeaconBlock.create(testDefinition.getSpec(), anchorBlock, BLSSignature.empty());
  }

  private void runSteps(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final ExecutionLayerChannelStub executionLayer)
      throws IOException {
    final List<Map<String, Object>> steps = loadSteps(testDefinition);
    for (Map<String, Object> step : steps) {
      LOG.info("Executing step {}", step);
      if (step.containsKey("checks")) {
        applyChecks(recentChainData, forkChoice, step);

      } else if (step.containsKey("tick")) {
        forkChoice.onTick(secondsToMillis(getUInt64(step, "tick")), Optional.empty());
        final UInt64 currentSlot = recentChainData.getCurrentSlot().orElse(UInt64.ZERO);
        LOG.info("Current slot: {} Epoch: {}", currentSlot, spec.computeEpochAtSlot(currentSlot));
      } else if (step.containsKey("block")) {
        applyBlock(testDefinition, spec, forkChoice, step, executionLayer);

      } else if (step.containsKey("attestation")) {
        applyAttestation(testDefinition, forkChoice, step);

      } else if (step.containsKey("pow_block")) {
        applyPowBlock(testDefinition, step, executionLayer);

      } else if (step.containsKey("attester_slashing")) {
        applyAttesterSlashing(testDefinition, forkChoice, step);

      } else if (step.containsKey("block_hash")) {
        applyPosBlock(step, executionLayer);

      } else {
        throw new UnsupportedOperationException("Unsupported step: " + step);
      }
    }
  }

  private void applyPowBlock(
      final TestDefinition testDefinition,
      final Map<String, Object> step,
      final ExecutionLayerChannelStub executionLayer) {
    final String filename = (String) step.get("pow_block");
    final PowBlock block =
        TestDataUtils.loadSsz(testDefinition, filename + ".ssz_snappy", this::parsePowBlock);
    executionLayer.addPowBlock(block);
  }

  private PowBlock parsePowBlock(final Bytes data) {
    return SSZ.decode(
        data,
        reader -> {
          final Bytes32 blockHash = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 parentHash = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final UInt256 totalDifficulty =
              UInt256.valueOf(
                  reader
                      .readFixedBytes(Bytes32.SIZE)
                      .toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN));
          // We don't get a timestamp but as long as it's in the past that's fine
          final UInt64 timestamp = UInt64.ZERO;
          return new PowBlock(blockHash, parentHash, totalDifficulty, timestamp);
        });
  }

  private void applyPosBlock(
      final Map<String, Object> step, final ExecutionLayerChannelStub executionLayer) {

    final Bytes32 blockHash = getBytes32(step, "block_hash");

    final Map<String, Object> payloadStatus = get(step, "payload_status");

    final PayloadStatus parsePayloadStatus = parsePayloadStatus(payloadStatus);

    executionLayer.addPosBlock(blockHash, parsePayloadStatus);
  }

  private PayloadStatus parsePayloadStatus(final Map<String, Object> payloadStatus) {
    final ExecutionPayloadStatus status =
        ExecutionPayloadStatus.valueOf(get(payloadStatus, "status"));
    final Optional<Bytes32> latestValidHash =
        getOptionalBytes32(payloadStatus, "latest_valid_hash");
    final Optional<String> validation_error =
        Optional.ofNullable(get(payloadStatus, "validation_error"));

    return PayloadStatus.create(status, latestValidHash, validation_error);
  }

  private void applyAttestation(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String attestationName = get(step, "attestation");
    final Attestation attestation =
        TestDataUtils.loadSsz(
            testDefinition,
            attestationName + ".ssz_snappy",
            testDefinition.getSpec().getGenesisSchemaDefinitions().getAttestationSchema());
    final Spec spec = testDefinition.getSpec();
    assertThat(forkChoice.onAttestation(ValidateableAttestation.from(spec, attestation)))
        .isCompleted();
  }

  private void applyAttesterSlashing(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String slashingName = get(step, "attester_slashing");
    final AttesterSlashing attesterSlashing =
        TestDataUtils.loadSsz(
            testDefinition,
            slashingName + ".ssz_snappy",
            testDefinition.getSpec().getGenesisSchemaDefinitions().getAttesterSlashingSchema());
    assertDoesNotThrow(
        () ->
            forkChoice.onAttesterSlashing(attesterSlashing, InternalValidationResult.ACCEPT, true));
  }

  private void applyBlock(
      final TestDefinition testDefinition,
      final Spec spec,
      final ForkChoice forkChoice,
      final Map<String, Object> step,
      final ExecutionLayerChannelStub executionLayer) {
    final String blockName = get(step, "block");
    final boolean valid = !step.containsKey("valid") || (boolean) step.get("valid");
    final SignedBeaconBlock block =
        TestDataUtils.loadSsz(
            testDefinition, blockName + ".ssz_snappy", spec::deserializeSignedBeaconBlock);
    LOG.info(
        "Importing block {} at slot {} with parent {}",
        block.getRoot(),
        block.getSlot(),
        block.getParentRoot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block, Optional.empty(), executionLayer);
    assertThat(result).isCompleted();
    final BlockImportResult importResult = result.join();
    assertThat(importResult)
        .describedAs("Incorrect block import result for block %s", block)
        .has(new Condition<>(r -> r.isSuccessful() == valid, "isSuccessful matching " + valid));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> loadSteps(final TestDefinition testDefinition)
      throws IOException {
    return TestDataUtils.loadYaml(testDefinition, "steps.yaml", List.class);
  }

  private void applyChecks(
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    assertThat(forkChoice.processHead()).isCompleted();
    final UpdatableStore store = recentChainData.getStore();
    final Map<String, Object> checks = get(step, "checks");
    final List<AssertionError> failures = new ArrayList<>();
    for (String checkType : checks.keySet()) {
      try {
        switch (checkType) {
          case "genesis_time":
            assertThat(recentChainData.getGenesisTime()).isEqualTo(getUInt64(checks, checkType));
            break;

          case "head":
            final Map<String, Object> expectedHead = get(checks, checkType);
            final UInt64 expectedSlot = UInt64.valueOf(expectedHead.get("slot").toString());
            final Bytes32 expectedRoot = Bytes32.fromHexString(expectedHead.get("root").toString());
            assertThat(recentChainData.getHeadSlot())
                .describedAs("best block slot")
                .isEqualTo(expectedSlot);
            assertThat(recentChainData.getBestBlockRoot())
                .describedAs("best block root")
                .contains(expectedRoot);
            break;

          case "time":
            final UInt64 expectedTime = getUInt64(checks, checkType);
            assertThat(store.getTimeSeconds()).describedAs("time").isEqualTo(expectedTime);
            break;

          case "justified_checkpoint_root":
            final Bytes32 expectedJustifiedRoot = getBytes32(checks, checkType);
            assertThat(store.getJustifiedCheckpoint().getRoot())
                .describedAs("justified checkpoint")
                .isEqualTo(expectedJustifiedRoot);
            break;

          case "justified_checkpoint":
            assertCheckpoint(
                "justified checkpoint", store.getJustifiedCheckpoint(), get(checks, checkType));
            break;

          case "best_justified_checkpoint":
            assertCheckpoint(
                "best justified checkpoint",
                store.getBestJustifiedCheckpoint(),
                get(checks, checkType));
            break;

          case "finalized_checkpoint_root":
            final Bytes32 expectedFinalizedRoot = getBytes32(checks, checkType);
            assertThat(store.getFinalizedCheckpoint().getRoot())
                .describedAs("finalized checkpoint")
                .isEqualTo(expectedFinalizedRoot);
            break;

          case "finalized_checkpoint":
            assertCheckpoint(
                "finalized checkpoint", store.getFinalizedCheckpoint(), get(checks, checkType));
            break;

          case "proposer_boost_root":
            final Optional<Bytes32> boostedRoot = store.getProposerBoostRoot();
            final Bytes32 expectedBoostedRoot = getBytes32(checks, checkType);
            if (expectedBoostedRoot.isZero()) {
              assertThat(boostedRoot).describedAs("proposer_boost_root").isEmpty();
            } else {
              assertThat(boostedRoot)
                  .describedAs("proposer_boost_root")
                  .contains(expectedBoostedRoot);
            }
            break;

          default:
            throw new UnsupportedOperationException("Unsupported check type: " + checkType);
        }
      } catch (final AssertionError failure) {
        failures.add(failure);
      }
    }

    if (!failures.isEmpty()) {
      final AssertionError firstError = failures.get(0);
      for (int i = 1; i < failures.size(); i++) {
        firstError.addSuppressed(failures.get(i));
      }
      throw firstError;
    }
  }

  private void assertCheckpoint(
      final String checkpointType,
      final Checkpoint actual,
      final Map<String, Object> expectedCheckpoint) {
    final Bytes32 expectedRoot = getBytes32(expectedCheckpoint, "root");
    final UInt64 expectedEpoch = getUInt64(expectedCheckpoint, "epoch");
    assertThat(actual)
        .describedAs(checkpointType)
        .isEqualTo(new Checkpoint(expectedEpoch, expectedRoot));
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private <T> T get(final Map<String, Object> yamlData, final String key) {
    return (T) yamlData.get(key);
  }

  private UInt64 getUInt64(final Map<String, Object> yamlData, final String key) {
    return UInt64.valueOf(get(yamlData, key).toString());
  }

  private Bytes32 getBytes32(final Map<String, Object> yamlData, final String key) {
    return Bytes32.fromHexString(get(yamlData, key));
  }

  private Optional<Bytes32> getOptionalBytes32(
      final Map<String, Object> yamlData, final String key) {
    return Optional.<String>ofNullable(get(yamlData, key)).map(Bytes32::fromHexString);
  }
}
