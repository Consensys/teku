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

package tech.pegasys.teku.benchmarks;

import static org.mockito.Mockito.mock;

import com.google.common.eventbus.EventBus;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.benchmarks.gen.BlockIO;
import tech.pegasys.teku.benchmarks.gen.BlockIO.Reader;
import tech.pegasys.teku.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.ForkChoiceAttestationValidator;
import tech.pegasys.teku.core.ForkChoiceBlockTasks;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.SyncForkChoiceExecutor;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

/** The test to be run manually for profiling block imports */
public class ProfilingRun {

  public static Consumer<Object> blackHole = o -> {};

  @Disabled
  @Test
  public void importBlocks() throws Exception {

    Constants.setConstants("mainnet");
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;

    int validatorsCount = 32 * 1024;
    int iterationBlockLimit = 1024;

    String blocksFile =
        "/blocks/blocks_epoch_"
            + Constants.SLOTS_PER_EPOCH
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";

    System.out.println("Generating keypairs...");

    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource("/bls-key-pairs/bls-key-pairs-200k-seed-0.txt.gz")
            .readAll(validatorsCount);

    BeaconState initialState =
        InteropStartupUtil.createMockedStartInitialBeaconState(0, validatorKeys, false);

    while (true) {
      EventBus localEventBus = mock(EventBus.class);
      RecentChainData recentChainData = MemoryOnlyRecentChainData.create(localEventBus);
      BeaconChainUtil localChain = BeaconChainUtil.create(recentChainData, validatorKeys, false);
      recentChainData.initializeFromGenesis(initialState);
      ForkChoice forkChoice =
          new ForkChoice(
              new ForkChoiceAttestationValidator(),
              new ForkChoiceBlockTasks(),
              new SyncForkChoiceExecutor(),
              recentChainData,
              new StateTransition());
      BlockImporter blockImporter =
          new BlockImporter(
              recentChainData, forkChoice, WeakSubjectivityValidator.lenient(), localEventBus);

      System.out.println("Start blocks import from " + blocksFile);
      int blockCount = 0;
      int measuredBlockCount = 0;

      long totalS = 0;
      try (Reader blockReader = BlockIO.createResourceReader(blocksFile)) {
        for (SignedBeaconBlock block : blockReader) {
          if (block.getSlot().intValue() == 65) {
            totalS = System.currentTimeMillis();
            measuredBlockCount = 0;
          }
          long s = System.currentTimeMillis();
          localChain.setSlot(block.getSlot());
          BlockImportResult result = blockImporter.importBlock(block).join();
          System.out.println(
              "Imported block at #"
                  + block.getSlot()
                  + " in "
                  + (System.currentTimeMillis() - s)
                  + " ms: "
                  + result);
          blockCount++;
          measuredBlockCount++;
          if (blockCount > iterationBlockLimit) break;
        }
      }
      long totalT = System.currentTimeMillis() - totalS;
      System.out.printf(
          "############# Total: %f.2 blocks/sec\n", measuredBlockCount / (totalT / 1000.0));
    }
  }

  public static void main(String[] args) throws Exception {
    new ProfilingRun().importBlocksMemProfiling();
  }

  @Disabled
  @Test
  public void importBlocksMemProfiling() throws Exception {

    Constants.setConstants("mainnet");
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;

    int validatorsCount = 32 * 1024;

    String blocksFile =
        "/blocks/blocks_epoch_"
            + Constants.SLOTS_PER_EPOCH
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";

    System.out.println("Generating keypairs...");

    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource("/bls-key-pairs/bls-key-pairs-200k-seed-0.txt.gz")
            .readAll(validatorsCount);

    BeaconState initialState =
        InteropStartupUtil.createMockedStartInitialBeaconState(0, validatorKeys, false);

    while (true) {
      EventBus localEventBus = mock(EventBus.class);
      RecentChainData recentChainData = MemoryOnlyRecentChainData.create(localEventBus);
      BeaconChainUtil localChain = BeaconChainUtil.create(recentChainData, validatorKeys, false);
      recentChainData.initializeFromGenesis(initialState);
      initialState = null;
      ForkChoice forkChoice =
          new ForkChoice(
              new ForkChoiceAttestationValidator(),
              new ForkChoiceBlockTasks(),
              new SyncForkChoiceExecutor(),
              recentChainData,
              new StateTransition());
      BlockImporter blockImporter =
          new BlockImporter(
              recentChainData, forkChoice, WeakSubjectivityValidator.lenient(), localEventBus);

      System.out.println("Start blocks import from " + blocksFile);
      int counter = 1;
      try (Reader blockReader = BlockIO.createResourceReader(blocksFile)) {
        for (SignedBeaconBlock block : blockReader) {
          long s = System.currentTimeMillis();
          localChain.setSlot(block.getSlot());
          BlockImportResult result = blockImporter.importBlock(block).join();
          System.out.println(
              "Imported block at #"
                  + block.getSlot()
                  + " in "
                  + (System.currentTimeMillis() - s)
                  + " ms: "
                  + result);

          if (--counter == 0) {

            // recreate View validator caches for older state
            //            traverseViewHierarchy(statesList.get(statesList.size() - 2), v ->
            // blackHole.accept(v));

            System.out.println("Press enter: ");
            String line =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                    .readLine();
            try {
              counter = Integer.parseInt(line);
            } catch (NumberFormatException e) {
              counter = 1;
            }
          }
        }
      }
    }
  }

  @Disabled
  @Test
  void runSszDeserialize() {
    BLSPublicKey publicKey = BLSPublicKey.random(1);
    System.out.println("Generating state...");
    BeaconState beaconState =
        new DataStructureUtil(1).withPubKeyGenerator(() -> publicKey).randomBeaconState(100_000);
    System.out.println("Serializing...");
    Bytes bytes = beaconState.sszSerialize();

    System.out.println("Deserializing...");
    while (true) {
      long s = System.currentTimeMillis();
      long sum = 0;
      for (int i = 0; i < 1; i++) {
        BeaconStateImpl state = SimpleOffsetSerializer.deserialize(bytes, BeaconStateImpl.class);
        blackHole.accept(state);
        for (Validator validator : state.getValidators()) {
          sum += validator.getEffective_balance().longValue();
        }
      }
      System.out.println("Time: " + (System.currentTimeMillis() - s) + ", sum = " + sum);
    }
  }
}
