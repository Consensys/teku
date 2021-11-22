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
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.block.BlockImportNotifications;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityFactory;
import tech.pegasys.teku.weaksubjectivity.WeakSubjectivityValidator;

/** The test to be run manually for profiling block imports */
public class ProfilingRun {
  public static Consumer<Object> blackHole = o -> {};
  private Spec spec = TestSpecFactory.createMainnetPhase0();

  @Disabled
  @Test
  public void importBlocks() throws Exception {

    Constants.setConstants("mainnet");
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;

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
        InteropStartupUtil.createMockedStartInitialBeaconState(spec, 0, validatorKeys, false);
    final WeakSubjectivityValidator wsValidator = WeakSubjectivityFactory.lenientValidator();

    while (true) {
      final BlockImportNotifications blockImportNotifications =
          mock(BlockImportNotifications.class);
      RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);
      recentChainData.initializeFromGenesis(initialState, UInt64.ZERO);
      ForkChoice forkChoice =
          ForkChoice.create(
              spec, new InlineEventThread(), recentChainData, mock(ForkChoiceNotifier.class));
      BeaconChainUtil localChain =
          BeaconChainUtil.create(spec, recentChainData, validatorKeys, false);
      BlockImporter blockImporter =
          new BlockImporter(
              blockImportNotifications,
              recentChainData,
              forkChoice,
              wsValidator,
              ExecutionEngineChannel.NOOP);

      System.out.println("Start blocks import from " + blocksFile);
      int blockCount = 0;
      int measuredBlockCount = 0;

      long totalS = 0;
      try (Reader blockReader = BlockIO.createResourceReader(spec, blocksFile)) {
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
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;

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
        InteropStartupUtil.createMockedStartInitialBeaconState(spec, 0, validatorKeys, false);
    final WeakSubjectivityValidator wsValidator = WeakSubjectivityFactory.lenientValidator();

    while (true) {
      final BlockImportNotifications blockImportNotifications =
          mock(BlockImportNotifications.class);
      RecentChainData recentChainData = MemoryOnlyRecentChainData.create();
      BeaconChainUtil localChain = BeaconChainUtil.create(recentChainData, validatorKeys, false);
      recentChainData.initializeFromGenesis(initialState, UInt64.ZERO);
      initialState = null;
      ForkChoice forkChoice =
          ForkChoice.create(
              spec, new InlineEventThread(), recentChainData, mock(ForkChoiceNotifier.class));
      BlockImporter blockImporter =
          new BlockImporter(
              blockImportNotifications,
              recentChainData,
              forkChoice,
              wsValidator,
              ExecutionEngineChannel.NOOP);

      System.out.println("Start blocks import from " + blocksFile);
      int counter = 1;
      try (Reader blockReader = BlockIO.createResourceReader(spec, blocksFile)) {
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
    BLSPublicKey publicKey = BLSTestUtil.randomPublicKey(1);
    System.out.println("Generating state...");
    BeaconState beaconState =
        new DataStructureUtil(1, spec)
            .withPubKeyGenerator(() -> publicKey)
            .randomBeaconState(100_000);
    final BeaconStateSchema<?, ?> stateSchema =
        spec.atSlot(beaconState.getSlot()).getSchemaDefinitions().getBeaconStateSchema();
    System.out.println("Serializing...");
    Bytes bytes = beaconState.sszSerialize();

    System.out.println("Deserializing...");

    while (true) {
      long s = System.currentTimeMillis();
      long sum = 0;
      for (int i = 0; i < 1; i++) {
        BeaconState state = stateSchema.sszDeserialize(bytes);
        blackHole.accept(state);
        for (Validator validator : state.getValidators()) {
          sum += validator.getEffective_balance().longValue();
        }
      }
      System.out.println("Time: " + (System.currentTimeMillis() - s) + ", sum = " + sum);
    }
  }
}
