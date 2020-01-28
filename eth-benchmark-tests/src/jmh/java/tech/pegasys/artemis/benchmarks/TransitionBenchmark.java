/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.benchmarks;

import static org.mockito.Mockito.mock;

import com.google.common.eventbus.EventBus;
import java.util.Iterator;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
import tech.pegasys.artemis.benchmarks.gen.BlockIO;
import tech.pegasys.artemis.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.blockimport.BlockImporter;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.Constants;

/** JMH base class for measuring state transitions performance */
@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Thread)
@Fork(0)
@Threads(1)
public abstract class TransitionBenchmark {

  ChainStorageClient localStorage;
  BeaconChainUtil localChain;
  BlockImporter blockImporter;
  Iterator<SignedBeaconBlock> blockIterator;
  BlockImportResult lastResult;
  SignedBeaconBlock prefetchedBlock;

  @Param({"1024", "3072", "10240"})
  int validatorsCount;

  @Setup(Level.Trial)
  public void init() throws Exception {
    Constants.SLOTS_PER_EPOCH = 6;
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;
    BeaconStateUtil.DEPOSIT_PROOFS_ENABLED = false;

    String blocksFile =
        "/blocks/blocks_epoch_"
            + Constants.SLOTS_PER_EPOCH
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";
    String keysFile = "/bls-key-pairs/bls-key-pairs-100k-seed-0.txt.gz";

    System.out.println("Generating keypairs from " + keysFile);
    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource(keysFile).readAll(validatorsCount);

    EventBus localEventBus = mock(EventBus.class);
    localStorage = ChainStorageClient.memoryOnlyClient(localEventBus);
    localChain = BeaconChainUtil.create(localStorage, validatorKeys, false);
    localChain.initializeStorage();

    blockImporter = new BlockImporter(localStorage, localEventBus);
    blockIterator = BlockIO.createResourceReader(blocksFile).iterator();
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
    lastResult = blockImporter.importBlock(block);
    System.out.println("Imported: " + lastResult);
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
          && (lastResult.getBlockProcessingRecord().getBlock().getSlot().longValue() + 1)
                  % Constants.SLOTS_PER_EPOCH
              == 0) {

        // import block with epoch transition
        importNextBlock();
      }
      prefetchBlock();
    }

    @Benchmark
    @Warmup(iterations = 2, batchSize = 6)
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
          || (lastResult.getBlockProcessingRecord().getBlock().getSlot().longValue() + 1)
                  % Constants.SLOTS_PER_EPOCH
              != 0) {
        importNextBlock();
      }
      prefetchBlock();
    }

    @Benchmark
    @Warmup(iterations = 2)
    @Measurement(iterations = 10)
    public void importBlock() throws Exception {
      importNextBlock();
    }
  }
}
