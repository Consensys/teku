/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.reference.mainnet.phase0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.reference.MapObjectUtil;
import tech.pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.util.ForkChoiceUtil;
import tech.pegasys.artemis.storage.Store;

@ExtendWith(BouncyCastleExtension.class)
@Disabled
class sanity_processing extends TestSuite {
  private static final Path configPath = Paths.get("mainnet", "phase0");

  {
    System.setProperty(
        "log4j.configuration", new File("resources", "log4j2.xml").toURI().toString());
  }

  @ParameterizedTest(name = "{index}. process sanity slots pre={0} -> post={1}. {arguments}")
  @MethodSource("sanitySlotsSetup")
  void processSlots(Context pre, Context post) throws Exception {
    System.out.println("c.path:" + pre.path);
    BeaconStateWithCache bs = (BeaconStateWithCache) pre.obj;

    BufferedReader inputStreamFromPath =
        new BufferedReader(
            new InputStreamReader(
                getInputStreamFromPath(Path.of(pre.path, "slots.yaml")), Charset.defaultCharset()));
    String s = inputStreamFromPath.readLine();

    new StateTransition(false).process_slots(bs, UnsignedLong.valueOf(s).plus(bs.getSlot()), true);

    assertEquals((BeaconStateWithCache) pre.obj, (BeaconStateWithCache) post.obj);
  }

  @MustBeClosed
  static Stream<Arguments> sanitySlotsSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "sanity", "slots", "pyspec_tests");
    return sanitySlotsProcessingSetup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process sanity blocks pre={0} -> post={1}. {arguments}")
  @MethodSource("sanityBlocksSetup")
  void processBlocks(Context pre, Context post, Context meta) throws Exception {
    System.out.println("c.path:" + pre.path);

    // get the val of blocks from the file
    String metaString = (String) meta.obj;
    int numBlocks =
        Integer.parseInt(
            metaString.substring(metaString.indexOf(":") + 1, metaString.indexOf("}")).trim());

    ArrayList<BeaconBlock> blocks = new ArrayList<>();
    for (int i = 0; i < numBlocks; i++) {
      System.out.println("importing block:" + i);
      BeaconBlock b =
          (BeaconBlock)
              MapObjectUtil.convertMapToTypedObject(
                  BeaconBlock.class, pathToObject(Path.of(meta.path, "blocks_" + i + ".yaml")));
      blocks.add(b);
    }

    if (false) {
      BeaconStateWithCache bs = (BeaconStateWithCache) pre.obj;
      StateTransition st = new StateTransition(true);
      BeaconStateWithCache spre = (BeaconStateWithCache) pre.obj;
      BeaconStateWithCache spost = (BeaconStateWithCache) post.obj;

      for (int i = 0; i < numBlocks; i++) {
        System.out.println("dealing with block:" + i);
        BeaconBlock b =
            (BeaconBlock)
                MapObjectUtil.convertMapToTypedObject(
                    BeaconBlock.class, pathToObject(Path.of(meta.path, "blocks_" + i + ".yaml")));
        st.initiate(spre, b, false);
      }
      assertEquals(spre, spost);
    }

    // approach using ForkChoiceUtil
    if (true) {

      BeaconStateWithCache spre = (BeaconStateWithCache) pre.obj;
      BeaconStateWithCache spost = (BeaconStateWithCache) post.obj;

      StateTransition st = new StateTransition(true);

      BeaconStateWithCache beaconStateWithCache =
          BeaconStateUtil.initialize_beacon_state_from_eth1(
              spre.getEth1_data().getBlock_hash(),
              UnsignedLong.ZERO,
              DataStructureUtil.newDeposits(spre.getEth1_deposit_index().intValue()));

      final StateTransition stateTransition = new StateTransition(false);
      stateTransition.process_slots(beaconStateWithCache, blocks.get(0).getSlot(), true);

      stateTransition.process_slots(spre, blocks.get(0).getSlot(), true);

      st.initiate(spre, blocks.get(0));

      Store genesis_store = ForkChoiceUtil.get_genesis_store(spre);
      for (int i = 1; i < blocks.size(); i++) {
        ForkChoiceUtil.on_block(
            genesis_store, blocks.get(i), new StateTransition(true), new EventBus());
      }
      //      Object hash_tree_root = genesis_store.getFinalized_checkpoint().hash_tree_root();
      Bytes32 head = ForkChoiceUtil.get_head(genesis_store);
      BeaconState c = genesis_store.getBlockState(genesis_store.getBlock(head).getState_root());
      assertEquals(c, spost);
    }
  }

  @MustBeClosed
  static Stream<Arguments> sanityBlocksSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "sanity", "blocks", "pyspec_tests");
    return sanityBlocksProcessingSetup(path, configPath);
  }
}
