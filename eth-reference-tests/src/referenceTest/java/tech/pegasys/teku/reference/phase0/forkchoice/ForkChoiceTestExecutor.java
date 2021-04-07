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

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceTestExecutor implements TestExecutor {
  private static final Logger LOG = LogManager.getLogger();
  public static final ImmutableMap<String, TestExecutor> FORK_CHOICE_TEST_TYPES =
      ImmutableMap.of("fork_choice/get_head", new ForkChoiceTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    // Note: The fork choice spec says there may be settings in a meta.yaml file but currently no
    // tests actually have one, so we currently don't bother trying to load it.
    final BeaconState anchorState =
        TestDataUtils.loadStateFromSsz(testDefinition, "anchor_state.ssz_snappy");
    final Spec spec = testDefinition.getSpec();
    final BeaconBlock anchorBlock =
        TestDataUtils.loadSsz(
            testDefinition, "anchor_block.ssz_snappy", spec::deserializeBeaconBlock);

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault();
    final RecentChainData recentChainData = storageSystem.recentChainData();
    recentChainData.initializeFromAnchorPoint(
        AnchorPoint.fromInitialBlockAndState(
            new SignedBlockAndState(
                SignedBeaconBlock.create(spec, anchorBlock, BLSSignature.empty()), anchorState)),
        spec.getSlotStartTime(anchorBlock.getSlot(), anchorState.getGenesis_time()));

    final ForkChoice forkChoice =
        ForkChoice.create(spec, new InlineEventThread(), recentChainData, false);

    runSteps(testDefinition, spec, recentChainData, forkChoice);
  }

  private void runSteps(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice)
      throws java.io.IOException {
    final List<Map<String, Object>> steps = loadSteps(testDefinition);
    for (Map<String, Object> step : steps) {
      LOG.info("Executing step {}", step);
      if (step.containsKey("checks")) {
        applyChecks(recentChainData, forkChoice, step);

      } else if (step.containsKey("tick")) {
        forkChoice.onTick(getUInt64(step, "tick"));

      } else if (step.containsKey("block")) {
        applyBlock(testDefinition, spec, forkChoice, step);

      } else if (step.containsKey("attestation")) {
        applyAttestation(testDefinition, forkChoice, step);

      } else {
        throw new UnsupportedOperationException("Unsupported step: " + step);
      }
    }
  }

  private void applyAttestation(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String attestationName = get(step, "attestation");
    final Attestation attestation =
        TestDataUtils.loadSsz(
            testDefinition, attestationName + ".ssz_snappy", Attestation.SSZ_SCHEMA);
    assertThat(forkChoice.onAttestation(ValidateableAttestation.from(attestation))).isCompleted();
  }

  private void applyBlock(
      final TestDefinition testDefinition,
      final Spec spec,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String blockName = get(step, "block");
    final SignedBeaconBlock block =
        TestDataUtils.loadSsz(
            testDefinition, blockName + ".ssz_snappy", spec::deserializeSignedBeaconBlock);
    assertThat(forkChoice.onBlock(block)).isCompleted();
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

        case "best_justified_checkpoint":
          final Bytes32 expectedBestJustifiedRoot = getBytes32(checks, checkType);
          assertThat(store.getBestJustifiedCheckpoint().getRoot())
              .describedAs("best justified checkpoint")
              .isEqualTo(expectedBestJustifiedRoot);
          break;

        case "finalized_checkpoint_root":
          final Bytes32 expectedFinalizedRoot = getBytes32(checks, checkType);
          assertThat(store.getFinalizedCheckpoint().getRoot())
              .describedAs("finalized checkpoint")
              .isEqualTo(expectedFinalizedRoot);
          break;

        default:
          throw new UnsupportedOperationException("Unsupported check type: " + checkType);
      }
    }
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
