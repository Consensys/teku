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

import java.util.Iterator;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.benchmarks.gen.BlockIO;
import tech.pegasys.teku.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
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
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Thread)
@Threads(1)
public abstract class TransitionBenchmark {
  Spec spec;
  WeakSubjectivityValidator wsValidator;
  RecentChainData recentChainData;
  BeaconChainUtil localChain;
  BlockImporter blockImporter;
  Iterator<SignedBeaconBlock> blockIterator;
  BlockImportResult lastResult;
  SignedBeaconBlock prefetchedBlock;

  @Param({"400000"})
  int validatorsCount;

  @Setup(Level.Trial)
  public void init() throws Exception {
    spec = TestSpecFactory.createMainnetAltair();
    AbstractBlockProcessor.depositSignatureVerifier = BLSSignatureVerifier.NO_OP;

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
    wsValidator = WeakSubjectivityFactory.lenientValidator();
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData, ExecutionLayerChannel.NOOP);
    ForkChoice forkChoice =
        new ForkChoice(
            spec,
            new InlineEventThread(),
            recentChainData,
            BlobSidecarManager.NOOP,
            new StubForkChoiceNotifier(),
            transitionBlockValidator,
            new StubMetricsSystem());
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
    System.out.println("Importing blocks from " + blocksFile);
  }

  @TearDown
  public void dispose() throws Exception {}

  protected void prefetchBlock() {
    prefetchedBlock = blockIterator.next();
  }

  protected void importNextBlock() {
    SignedBeaconBlock block;
    if (prefetchedBlock == null) {
      block = blockIterator.next();
    } else {
      block = prefetchedBlock;
      prefetchedBlock = null;
    }
    localChain.setSlot(block.getSlot());
    lastResult = blockImporter.importBlock(block).join();
    if (!lastResult.isSuccessful()) {
      throw new RuntimeException("Unable to import block: " + lastResult);
    }
  }

  /**
   * Measures pure block transition performance by importing epoch boundary blocks outside of the
   * benchmark method
   */
  public static class Block extends TransitionBenchmark {

    @Setup(Level.Iteration)
    public void skipAndPrefetch() throws Exception {
      if (lastResult != null
          && (lastResult.getBlock().getSlot().longValue() + 1) % spec.getSlotsPerEpoch(UInt64.ZERO)
              == 0) {

        // import block with epoch transition
        importNextBlock();
      }
      prefetchBlock();
    }

    @Benchmark
    @Warmup(iterations = 2, batchSize = 32)
    @Measurement(iterations = 50)
    public void importBlock() throws Exception {
      importNextBlock();
    }
  }

  /**
   * Measures epoch state transition performance by importing only epoch boundary blocks in the
   * benchmark method. Other blocks are 'skipped' by importing them outside of benchmark method.
   * NOTE: the resulting time would include block AND epoch transition
   */
  public static class Epoch extends TransitionBenchmark {
    @Setup(Level.Iteration)
    public void skipAndPrefetch() throws Exception {
      // import all blocks without epoch transition
      while (lastResult == null
          || (lastResult.getBlock().getSlot().longValue() + 1) % spec.getSlotsPerEpoch(UInt64.ZERO)
              != 0) {
        importNextBlock();
      }
      prefetchBlock();
    }

    @Benchmark
    @Warmup(iterations = 10)
    @Measurement(iterations = 20)
    public void importBlock() throws Exception {
      importNextBlock();
    }
  }
}
