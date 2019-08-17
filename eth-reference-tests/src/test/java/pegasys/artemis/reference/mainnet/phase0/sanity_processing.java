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

package pegasys.artemis.reference.mainnet.phase0;
import com.google.common.primitives.UnsignedLong;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.MapObjectUtil;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.*;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.util.BlockProcessorUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
class sanity_processing extends TestSuite {
  private static final Path configPath = Paths.get("mainnet", "phase0");


  @ParameterizedTest(name = "{index}. process sanity slots pre={0} -> post={1}. {arguments}")
  @MethodSource("sanitySlotsSetup")
  void processSlots(Context pre, Context post, Context slots) throws Exception {
    System.out.println("c.path:"+pre.path);
    BeaconStateWithCache bs = (BeaconStateWithCache) pre.obj;
    StateTransition.process_slots(bs, UnsignedLong.valueOf((String)slots.obj).plus(bs.getSlot()), true);

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
    System.out.println("c.path:"+pre.path);
    BeaconStateWithCache bs = (BeaconStateWithCache) pre.obj;
    StateTransition st = new StateTransition(true);
    BeaconStateWithCache spre = (BeaconStateWithCache) pre.obj;
    BeaconStateWithCache spost = (BeaconStateWithCache) post.obj;

    // get the val of blocks from the file
    String metaString = (String) meta.obj;
    int numm = Integer.parseInt(metaString.substring(metaString.indexOf(":")+1,metaString.indexOf("}")).trim());

    for (int i = 0; i<numm; i++) {
      System.out.println("dealing with block:"+i);
      BeaconBlock b = (BeaconBlock) MapObjectUtil.convertMapToTypedObject(BeaconBlock.class, pathToObject(Path.of(meta.path,"blocks_"+i+".yaml")));
      st.initiate(spre, b, false);
    }

    assertEquals(spre, spost);
  }

  @MustBeClosed
  static Stream<Arguments> sanityBlocksSetup() throws Exception {
    Path path = Paths.get("mainnet", "phase0", "sanity", "blocks", "pyspec_tests");
    return sanityBlocksProcessingSetup(path, configPath);
  }

}
