/*
 * Copyright 2021 ConsenSys AG.
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
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.StubExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
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
          .put(
              "fork_choice/on_block",
              new ForkChoiceTestExecutor("new_finalized_slot_is_justified_checkpoint_ancestor"))
          .put("fork_choice/on_merge_block", IGNORE_TESTS)
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
        spec.getSlotStartTime(anchorBlock.getSlot(), anchorState.getGenesis_time()));

    final ForkChoice forkChoice =
        ForkChoice.create(
            spec, new InlineEventThread(), recentChainData, mock(ForkChoiceNotifier.class), true);
    final StubExecutionEngineChannel executionEngine = new StubExecutionEngineChannel(spec);

    runSteps(testDefinition, spec, recentChainData, forkChoice, executionEngine);
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
      final StubExecutionEngineChannel executionEngine)
      throws java.io.IOException {
    final List<Map<String, Object>> steps = loadSteps(testDefinition);
    for (Map<String, Object> step : steps) {
      LOG.info("Executing step {}", step);
      if (step.containsKey("checks")) {
        applyChecks(recentChainData, forkChoice, step);

      } else if (step.containsKey("tick")) {
        forkChoice.onTick(getUInt64(step, "tick"));

      } else if (step.containsKey("block")) {
        applyBlock(testDefinition, spec, forkChoice, step, executionEngine);

      } else if (step.containsKey("attestation")) {
        applyAttestation(testDefinition, forkChoice, step);

      } else if (step.containsKey("pow_block")) {
        applyPowBlock(testDefinition, step, executionEngine);

      } else {
        throw new UnsupportedOperationException("Unsupported step: " + step);
      }
    }
  }

  private void applyPowBlock(
      final TestDefinition testDefinition,
      final Map<String, Object> step,
      final StubExecutionEngineChannel executionEngine) {
    final String filename = (String) step.get("pow_block");
    final PowBlock block =
        TestDataUtils.loadSsz(testDefinition, filename + ".ssz_snappy", this::parsePowBlock);
    executionEngine.addPowBlock(block);
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
          reader.readFixedBytes(Bytes32.SIZE); // Read difficulty even though we don't use it.
          return new PowBlock(blockHash, parentHash, totalDifficulty);
        });
  }

  private void applyAttestation(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String attestationName = get(step, "attestation");
    final Attestation attestation =
        TestDataUtils.loadSsz(
            testDefinition, attestationName + ".ssz_snappy", Attestation.SSZ_SCHEMA);
    final Spec spec = testDefinition.getSpec();
    assertThat(forkChoice.onAttestation(ValidateableAttestation.from(spec, attestation)))
        .isCompleted();
  }

  private void applyBlock(
      final TestDefinition testDefinition,
      final Spec spec,
      final ForkChoice forkChoice,
      final Map<String, Object> step,
      final StubExecutionEngineChannel executionEngine) {
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
    final SafeFuture<BlockImportResult> result = forkChoice.onBlock(block, executionEngine);
    assertThat(result).isCompleted();
    final BlockImportResult importResult = result.join();
    assertThat(importResult)
        .describedAs("Incorrect block import result for block %s", block)
        .has(new Condition<>(r -> r.isSuccessful() == valid, "isSuccessful matching " + valid));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> loadSteps(final TestDefinition testDefinition)
      throws java.io.IOException {
    return TestDataUtils.loadYaml(testDefinition, "steps.yaml", List.class);
  }

  private void applyChecks(
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    assertThat(forkChoice.processHead()).isCompleted();
    final UpdatableStore store = recentChainData.getStore();
    final Map<String, Object> checks = get(step, "checks");
    for (String checkType : checks.keySet()) {

      switch (checkType) {
        case "genesis_time":
          assertThat(recentChainData.getGenesisTime()).isEqualTo(getUInt64(checks, checkType));
          break;

        case "head":
          final Map<String, Object> expectedHead = get(checks, checkType);
          final UInt64 expectedSlot = UInt64.valueOf(expectedHead.get("slot").toString());
          final Bytes32 expectedRoot = Bytes32.fromHexString(expectedHead.get("root").toString());
          assertThat(recentChainData.getHeadSlot()).isEqualTo(expectedSlot);
          assertThat(recentChainData.getBestBlockRoot()).contains(expectedRoot);
          break;

        case "time":
          final UInt64 expectedTime = getUInt64(checks, checkType);
          assertThat(store.getTime()).isEqualTo(expectedTime);
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
}
