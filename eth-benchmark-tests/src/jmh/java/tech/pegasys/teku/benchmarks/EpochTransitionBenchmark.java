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

package tech.pegasys.teku.benchmarks;

import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.gen.BlockIO;
import tech.pegasys.teku.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.teku.benchmarks.util.CustomRunner;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas.RewardAndPenalty;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.block.BlockImportNotifications;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.StubForkChoiceNotifier;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityFactory;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

/** JMH base class for measuring state transitions performance */
@Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Threads(1)
@Fork(1)
public class EpochTransitionBenchmark {
  Spec spec;
  WeakSubjectivityValidator wsValidator;
  RecentChainData recentChainData;
  BeaconChainUtil localChain;
  BlockImporter blockImporter;
  Iterator<SignedBeaconBlock> blockIterator;
  BlockImportResult lastResult;

  EpochProcessor epochProcessor;
  BeaconState preEpochTransitionState;
  MutableBeaconState preEpochTransitionMutableState;
  ValidatorStatuses validatorStatuses;
  RewardAndPenaltyDeltas attestationDeltas;

  @Param({"400000"})
  int validatorsCount = 400000;

  @Setup(Level.Trial)
  public void init() throws Exception {
    AbstractBlockProcessor.blsVerifyDeposit = false;

    spec = TestSpecFactory.createMainnetAltair();
    String blocksFile =
        "/blocks/blocks_epoch_"
            + spec.getSlotsPerEpoch(UInt64.ZERO)
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";
    String keysFile = "/bls-key-pairs/bls-key-pairs-400k-seed-0.txt.gz";

    System.out.println("Generating keypairs from " + keysFile);
    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource(keysFile).readAll(validatorsCount);

    final BlockImportNotifications blockImportNotifications = mock(BlockImportNotifications.class);
    epochProcessor = spec.getGenesisSpec().getEpochProcessor();
    wsValidator = WeakSubjectivityFactory.lenientValidator();

    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData, ExecutionLayerChannel.NOOP);
    ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            recentChainData,
            new StubForkChoiceNotifier(),
            transitionBlockValidator);
    localChain = BeaconChainUtil.create(spec, recentChainData, validatorKeys, false);
    localChain.initializeStorage();

    blockImporter =
        new BlockImporter(
            spec,
            blockImportNotifications,
            recentChainData,
            forkChoice,
            wsValidator,
            ExecutionLayerChannel.NOOP);
    blockIterator = BlockIO.createResourceReader(spec, blocksFile).iterator();
    System.out.println("Importing 63 blocks from " + blocksFile);

    for (int i = 0; i < 63; i++) {
      SignedBeaconBlock block = blockIterator.next();
      localChain.setSlot(block.getSlot());
      lastResult = blockImporter.importBlock(block).join();
    }

    preEpochTransitionState = safeJoin(recentChainData.getBestState().orElseThrow());
    preEpochTransitionMutableState =
        (MutableBeaconState) preEpochTransitionState.createWritableCopy();

    validatorStatuses =
        spec.getGenesisSpec()
            .getValidatorStatusFactory()
            .createValidatorStatuses(preEpochTransitionState);
    preEpochTransitionState.hashTreeRoot();
    attestationDeltas =
        epochProcessor.getRewardAndPenaltyDeltas(preEpochTransitionState, validatorStatuses);

    System.out.println("Done!");
  }

  @Benchmark
  public void epochTransition(Blackhole bh) {
    try {
      preEpochTransitionState = epochProcessor.processEpoch(preEpochTransitionState);
      bh.consume(preEpochTransitionState.hashTreeRoot());
    } catch (EpochProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public void createValidatorStatuses(Blackhole bh) {
    final ValidatorStatuses statuses =
        spec.atSlot(preEpochTransitionState.getSlot())
            .getValidatorStatusFactory()
            .createValidatorStatuses(preEpochTransitionState);
    bh.consume(statuses);
  }

  @Benchmark
  public void processRewardsAndPenalties(Blackhole bh) {
    try {
      epochProcessor.processRewardsAndPenalties(preEpochTransitionMutableState, validatorStatuses);
    } catch (EpochProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Benchmark
  public void applyDeltas(Blackhole bh) {
    final SszMutableUInt64List balances = preEpochTransitionMutableState.getBalances();
    int validatorsSize = preEpochTransitionMutableState.getValidators().size();
    for (int i = 0; i < validatorsSize; i++) {
      final RewardAndPenalty delta = attestationDeltas.getDelta(i);
      balances.setElement(
          i, balances.getElement(i).plus(delta.getReward()).minusMinZero(delta.getPenalty()));
    }
  }

  public static void main(String[] args) throws Exception {
    EpochTransitionBenchmark benchmark = new EpochTransitionBenchmark();
    benchmark.init();

    new CustomRunner(20, 100000).withBench(benchmark::epochTransition).run();
  }
}
