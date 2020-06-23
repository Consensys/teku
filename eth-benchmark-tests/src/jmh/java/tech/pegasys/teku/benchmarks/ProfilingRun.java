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
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.benchmarks.gen.BlockIO;
import tech.pegasys.teku.benchmarks.gen.BlockIO.Reader;
import tech.pegasys.teku.benchmarks.gen.BlsKeyPairIO;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.blockimport.BlockImporter;
import tech.pegasys.teku.statetransition.util.StartupUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.util.hashtree.HashTreeUtil;
import tech.pegasys.teku.util.hashtree.HashTreeUtil.SSZTypes;

/** The test to be run manually for profiling block imports */
public class ProfilingRun {

  @Disabled
  @Test
  public void importBlocks() throws Exception {

    Constants.setConstants("mainnet");
    BeaconStateUtil.BLS_VERIFY_DEPOSIT = false;

    int validatorsCount = 16 * 1024;

    String blocksFile =
        "/blocks/blocks_epoch_"
            + Constants.SLOTS_PER_EPOCH
            + "_validators_"
            + validatorsCount
            + ".ssz.gz";

    System.out.println("Generating keypairs...");
    //    List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(validatorsCount);
    //    List<BLSKeyPair> validatorKeys =
    // BlsKeyPairIO.createReaderWithDefaultSource().readAll(validatorsCount);
    List<BLSKeyPair> validatorKeys =
        BlsKeyPairIO.createReaderForResource("/bls-key-pairs/bls-key-pairs-200k-seed-0.txt.gz")
            .readAll(validatorsCount);

    BeaconState initialState =
        StartupUtil.createMockedStartInitialBeaconState(0, validatorKeys, false);

    while (true) {
      EventBus localEventBus = mock(EventBus.class);
      RecentChainData recentChainData = MemoryOnlyRecentChainData.create(localEventBus);
      BeaconChainUtil localChain = BeaconChainUtil.create(recentChainData, validatorKeys, false);
      recentChainData.initializeFromGenesis(initialState);
      BlockImporter blockImporter = new BlockImporter(recentChainData, localEventBus);

      System.out.println("Start blocks import from " + blocksFile);
      try (Reader blockReader = BlockIO.createResourceReader(blocksFile)) {
        for (SignedBeaconBlock block : blockReader) {
          long s = System.currentTimeMillis();
          localChain.setSlot(block.getSlot());
          BlockImportResult result = blockImporter.importBlock(block);
          //        compareHashes(result.getBlockProcessingRecord().getPostState());
          System.out.println(
              "Imported block at #"
                  + block.getSlot()
                  + " in "
                  + (System.currentTimeMillis() - s)
                  + " ms: "
                  + result);
        }
      }
    }
  }

  void compareHashes(BeaconState s1) {
    for (int i = 0; i < s1.size(); i++) {
      Bytes32 hash = s1.get(i).hashTreeRoot();
      System.out.println(i + ": " + hash);
    }
    System.out.println("BS: " + s1.hash_tree_root());
    System.out.println("BS: " + old_hash_tree_root(s1));
    System.out.println("getEth1_data: " + s1.getEth1_data().hash_tree_root());
    System.out.println("getEth1_data: " + s1.getEth1_data().hash_tree_root());
    System.out.println("getValidators: " + s1.getValidators().hash_tree_root());
    System.out.println(
        "getValidators: "
            + HashTreeUtil.hash_tree_root(
                HashTreeUtil.SSZTypes.LIST_OF_COMPOSITE, s1.getValidators()));
    System.out.println("getBlock_roots: " + s1.getBlock_roots().hash_tree_root());
    System.out.println(
        "getBlock_roots: "
            + HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s1.getBlock_roots()));

    System.out.println("getHistorical_roots: " + s1.getHistorical_roots().hash_tree_root());
    System.out.println(
        "getHistorical_roots: "
            + HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, s1.getHistorical_roots()));

    System.out.println("getEth1_data_votes: " + s1.getEth1_data_votes().hash_tree_root());
    System.out.println(
        "getEth1_data_votes: "
            + HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, s1.getEth1_data_votes()));

    System.out.println("getBalances: " + s1.getBalances().hash_tree_root());
    System.out.println(
        "getBalances: "
            + HashTreeUtil.hash_tree_root_list_ul(
                s1.getBalances().map(Bytes.class, item -> SSZ.encodeUInt64(item.longValue()))));

    System.out.println(
        "getJustification_bits: "
            + HashTreeUtil.hash_tree_root_bitvector(s1.getJustification_bits()));
  }

  public Bytes32 old_hash_tree_root(BeaconState s) {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // Versioning
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(s.getGenesis_time().longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(s.getSlot().longValue())),
            s.getFork().hash_tree_root(),

            // History
            s.getLatest_block_header().hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s.getBlock_roots()),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s.getState_roots()),
            HashTreeUtil.hash_tree_root_list_bytes(s.getHistorical_roots()),

            // Ethereum 1.0 chain data
            s.getEth1_data().hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, s.getEth1_data_votes()),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(s.getEth1_deposit_index().longValue())),

            // Validator registry
            HashTreeUtil.hash_tree_root(SSZTypes.LIST_OF_COMPOSITE, s.getValidators()),
            HashTreeUtil.hash_tree_root_list_ul(
                s.getBalances().map(Bytes.class, item -> SSZ.encodeUInt64(item.longValue()))),

            // Randomness
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_COMPOSITE, s.getRandao_mixes()),

            // Slashings
            HashTreeUtil.hash_tree_root_vector_unsigned_long(s.getSlashings()),

            // Attestations
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE, s.getPrevious_epoch_attestations()),
            HashTreeUtil.hash_tree_root(
                SSZTypes.LIST_OF_COMPOSITE, s.getCurrent_epoch_attestations()),

            // Finality
            HashTreeUtil.hash_tree_root_bitvector(s.getJustification_bits()),
            s.getPrevious_justified_checkpoint().hash_tree_root(),
            s.getCurrent_justified_checkpoint().hash_tree_root(),
            s.getFinalized_checkpoint().hash_tree_root()));
  }
}
