/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
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
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.reference.KzgRetriever;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
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
import tech.pegasys.teku.statetransition.datacolumns.DasSamplerManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceTestExecutor implements TestExecutor {
  private static final Logger LOG = LogManager.getLogger();
  private static final String SSZ_SNAPPY_EXTENSION = ".ssz_snappy";
  public static final ImmutableMap<String, TestExecutor> FORK_CHOICE_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("fork_choice/get_head", new ForkChoiceTestExecutor())
          .put("fork_choice/ex_ante", new ForkChoiceTestExecutor())
          .put("fork_choice/reorg", new ForkChoiceTestExecutor())
          .put("fork_choice/on_block", new ForkChoiceTestExecutor())
          .put("fork_choice/on_merge_block", new ForkChoiceTestExecutor())
          .put("fork_choice/withholding", new ForkChoiceTestExecutor())
          .put("sync/optimistic", new ForkChoiceTestExecutor())
          .put("fork_choice/should_override_forkchoice_update", new ForkChoiceTestExecutor())
          .put("fork_choice/get_proposer_head", new ForkChoiceTestExecutor("basic_is_parent_root"))
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
    final Spec spec = testDefinition.getSpec();
    final BeaconState anchorState =
        TestDataUtils.loadStateFromSsz(testDefinition, "anchor_state" + SSZ_SNAPPY_EXTENSION);
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
    final InlineEventThread eventThread = new InlineEventThread();
    final KZG kzg = KzgRetriever.getKzgWithLoadedTrustedSetup(spec, testDefinition.getConfigName());
    final StubBlobSidecarManager blobSidecarManager = new StubBlobSidecarManager(kzg);
    // forkChoiceLateBlockReorgEnabled is true here always because this is the reference test
    // executor
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            blobSidecarManager,
            DasSamplerManager.NOOP,
            new NoopForkChoiceNotifier(),
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            true,
            storageSystem.getMetricsSystem());
    final ExecutionLayerChannelStub executionLayer =
        new ExecutionLayerChannelStub(spec, false, Optional.empty());

    try {
      runSteps(
          testDefinition, spec, recentChainData, blobSidecarManager, forkChoice, executionLayer);
    } catch (final AssertionError e) {
      final String protoArrayData =
          recentChainData.getForkChoiceStrategy().orElseThrow().getBlockData().stream()
              .map(Object::toString)
              .collect(Collectors.joining("\n"));
      throw new AssertionError(
          e.getMessage()
              + "\n-------"
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
   * doing Altair genesis. See https://github.com/ethereum/consensus-specs/pull/2323
   *
   * @param testDefinition the test definition
   * @return the anchor block for the test
   */
  private SignedBeaconBlock loadAnchorBlock(final TestDefinition testDefinition) {
    final BeaconBlock anchorBlock =
        TestDataUtils.loadSsz(
            testDefinition,
            "anchor_block" + SSZ_SNAPPY_EXTENSION,
            testDefinition.getSpec()::deserializeBeaconBlock);
    return SignedBeaconBlock.create(testDefinition.getSpec(), anchorBlock, BLSSignature.empty());
  }

  private void runSteps(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final StubBlobSidecarManager blobSidecarManager,
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
        applyBlock(
            testDefinition,
            spec,
            recentChainData,
            blobSidecarManager,
            forkChoice,
            step,
            executionLayer);

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
        TestDataUtils.loadSsz(testDefinition, filename + SSZ_SNAPPY_EXTENSION, this::parsePowBlock);
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
        getOptionallyBytes32(payloadStatus, "latest_valid_hash");
    final Optional<String> validationError = getOptionally(payloadStatus, "validation_error");

    return PayloadStatus.create(status, latestValidHash, validationError);
  }

  private void applyAttestation(
      final TestDefinition testDefinition,
      final ForkChoice forkChoice,
      final Map<String, Object> step) {
    final String attestationName = get(step, "attestation");
    final Attestation attestation =
        TestDataUtils.loadSsz(
            testDefinition,
            attestationName + SSZ_SNAPPY_EXTENSION,
            testDefinition.getSpec().getGenesisSchemaDefinitions().getAttestationSchema());
    final Spec spec = testDefinition.getSpec();
    assertThat(forkChoice.onAttestation(ValidatableAttestation.from(spec, attestation)))
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
            slashingName + SSZ_SNAPPY_EXTENSION,
            testDefinition.getSpec().getGenesisSchemaDefinitions().getAttesterSlashingSchema());
    assertDoesNotThrow(
        () ->
            forkChoice.onAttesterSlashing(attesterSlashing, InternalValidationResult.ACCEPT, true));
  }

  private void applyBlock(
      final TestDefinition testDefinition,
      final Spec spec,
      final RecentChainData recentChainData,
      final StubBlobSidecarManager blobSidecarManager,
      final ForkChoice forkChoice,
      final Map<String, Object> step,
      final ExecutionLayerChannelStub executionLayer) {
    final String blockName = get(step, "block");
    final boolean valid = !step.containsKey("valid") || (boolean) step.get("valid");
    final SignedBeaconBlock block =
        TestDataUtils.loadSsz(
            testDefinition, blockName + SSZ_SNAPPY_EXTENSION, spec::deserializeSignedBeaconBlock);
    final List<Blob> blobs =
        getOptionally(step, "blobs")
            .map(
                blobsName ->
                    TestDataUtils.loadSsz(
                            testDefinition,
                            blobsName + SSZ_SNAPPY_EXTENSION,
                            sszBytes -> spec.deserializeBlobsInBlock(sszBytes, block.getSlot()))
                        .asList())
            .orElse(Collections.emptyList());
    @SuppressWarnings("unchecked")
    final List<KZGProof> proofs =
        getOptionally(step, "proofs")
            .map(
                proofsArray ->
                    ((List<String>) proofsArray).stream().map(KZGProof::fromHexString).toList())
            .orElse(Collections.emptyList());
    if (block.getMessage().getBody().toVersionDeneb().isPresent()) {
      LOG.info(
          "Preparing {} blobs with proofs {} for block {}", blobs.size(), proofs, block.getRoot());
      blobSidecarManager.prepareBlobsAndProofsForBlock(block, blobs, proofs);
    }
    LOG.info(
        "Importing block {} at slot {} with parent {}",
        block.getRoot(),
        block.getSlot(),
        block.getParentRoot());
    final SafeFuture<BlockImportResult> result =
        forkChoice.onBlock(block, Optional.empty(), BlockBroadcastValidator.NOOP, executionLayer);
    assertThat(result).isCompleted();
    final BlockImportResult importResult = safeJoin(result);
    assertThat(importResult)
        .describedAs("Incorrect block import result for block %s", block)
        .has(new Condition<>(r -> r.isSuccessful() == valid, "isSuccessful matching " + valid));

    if (valid
        && spec.computeEpochAtSlot(block.getSlot())
            .plus(1)
            .isLessThan(recentChainData.getCurrentEpoch().orElseThrow())) {
      applyAttestationWeight(spec, recentChainData, block);
    }
  }

  private static void applyAttestationWeight(
      final Spec spec, final RecentChainData recentChainData, final SignedBeaconBlock block) {
    // Apply attestations from block that we may have skipped because they're old
    // The spec tests currently require this but it doesn't make sense to incur this cost during
    // a sync where the attestation weighting is almost certainly useless.
    final BeaconState preState =
        safeJoin(
                recentChainData.retrieveStateAtSlot(
                    new SlotAndBlockRoot(block.getSlot(), block.getParentRoot())))
            .orElseThrow();
    final VoteUpdater voteUpdater = recentChainData.startVoteUpdate();
    final ForkChoiceStrategy forkChoiceStrategy =
        recentChainData.getUpdatableForkChoiceStrategy().orElseThrow();
    block
        .getMessage()
        .getBody()
        .getAttestations()
        .forEach(
            attestation ->
                forkChoiceStrategy.onAttestation(
                    voteUpdater,
                    spec.atSlot(block.getSlot())
                        .getAttestationUtil()
                        .getIndexedAttestation(preState, attestation)));
    voteUpdater.commit();
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
          case "genesis_time" -> assertThat(recentChainData.getGenesisTime())
              .isEqualTo(getUInt64(checks, checkType));

          case "head" -> {
            final Map<String, Object> expectedHead = get(checks, checkType);
            final UInt64 expectedSlot = UInt64.valueOf(expectedHead.get("slot").toString());
            final Bytes32 expectedRoot = Bytes32.fromHexString(expectedHead.get("root").toString());
            assertThat(recentChainData.getHeadSlot())
                .describedAs("best block slot")
                .isEqualTo(expectedSlot);
            assertThat(recentChainData.getBestBlockRoot())
                .describedAs("best block root")
                .contains(expectedRoot);
          }

          case "time" -> {
            final UInt64 expectedTime = getUInt64(checks, checkType);
            assertThat(store.getTimeSeconds()).describedAs("time").isEqualTo(expectedTime);
          }

          case "justified_checkpoint_root" -> {
            final Bytes32 expectedJustifiedRoot = getBytes32(checks, checkType);
            assertThat(store.getJustifiedCheckpoint().getRoot())
                .describedAs("justified checkpoint")
                .isEqualTo(expectedJustifiedRoot);
          }

          case "justified_checkpoint" -> assertCheckpoint(
              "justified checkpoint", store.getJustifiedCheckpoint(), get(checks, checkType));

          case "best_justified_checkpoint" -> assertCheckpoint(
              "best justified checkpoint",
              store.getBestJustifiedCheckpoint(),
              get(checks, checkType));

          case "finalized_checkpoint_root" -> {
            final Bytes32 expectedFinalizedRoot = getBytes32(checks, checkType);
            assertThat(store.getFinalizedCheckpoint().getRoot())
                .describedAs("finalized checkpoint")
                .isEqualTo(expectedFinalizedRoot);
          }

          case "finalized_checkpoint" -> assertCheckpoint(
              "finalized checkpoint", store.getFinalizedCheckpoint(), get(checks, checkType));

          case "proposer_boost_root" -> {
            final Optional<Bytes32> boostedRoot = store.getProposerBoostRoot();
            final Bytes32 expectedBoostedRoot = getBytes32(checks, checkType);
            if (expectedBoostedRoot.isZero()) {
              assertThat(boostedRoot).describedAs("proposer_boost_root").isEmpty();
            } else {
              assertThat(boostedRoot)
                  .describedAs("proposer_boost_root")
                  .contains(expectedBoostedRoot);
            }
          }

          case "get_proposer_head" -> {
            final Bytes32 expectedProposerHead = getBytes32(checks, checkType);
            final Bytes32 root =
                recentChainData.getProposerHead(
                    expectedProposerHead, recentChainData.getHeadSlot().increment());
            assertThat(root).describedAs("get_proposer_head").isEqualTo(expectedProposerHead);
          }

          case "should_override_forkchoice_update" -> {
            final Map<String, Boolean> shouldOverrideForkChoiceUpdateCheck = get(checks, checkType);
            final boolean expectedResult = shouldOverrideForkChoiceUpdateCheck.get("result");
            final boolean expectedValidatorIsConnected =
                shouldOverrideForkChoiceUpdateCheck.get("validator_is_connected");
            final boolean shouldOverrideChainHead =
                recentChainData.shouldOverrideForkChoiceUpdate(
                    recentChainData.getBestBlockRoot().orElseThrow());
            assertThat(shouldOverrideChainHead).isEqualTo(expectedResult);
            // We've currently only handled the validatorIsConnected 'true' case in reftests,
            // lets validate we're dealing with that and don't have to extend tests further.
            assertThat(expectedValidatorIsConnected).isTrue();
          }

          default -> throw new UnsupportedOperationException(
              "Unsupported check type: " + checkType);
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
  private static <T> T get(final Map<String, Object> yamlData, final String key) {
    return (T) yamlData.get(key);
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> Optional<T> getOptionally(
      final Map<String, Object> yamlData, final String key) {
    return Optional.ofNullable((T) yamlData.get(key));
  }

  private static UInt64 getUInt64(final Map<String, Object> yamlData, final String key) {
    return UInt64.valueOf(get(yamlData, key).toString());
  }

  private static Bytes32 getBytes32(final Map<String, Object> yamlData, final String key) {
    return Bytes32.fromHexString(get(yamlData, key));
  }

  private static Optional<Bytes32> getOptionallyBytes32(
      final Map<String, Object> yamlData, final String key) {
    return ForkChoiceTestExecutor.<String>getOptionally(yamlData, key).map(Bytes32::fromHexString);
  }
}
