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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SizeTest {

  public TreeNode stateTree;

  @Test
  void a() throws InterruptedException {
    stateTree = createStateTree();
    Thread.sleep(100000000000L);
  }

  TreeNode createStateTree() {
    BLSPublicKey pubKey = BLSPublicKey.random(1);
    DataStructureUtil dataStructureUtil = new DataStructureUtil().withPubKeyGenerator(() -> pubKey);
    System.out.println("Generating state...");
    BeaconState state = dataStructureUtil.randomBeaconState(100_000);
    System.out.println("Counting nodes...");
    //    AtomicInteger branchCount = new AtomicInteger();
    //    AtomicInteger leafCount = new AtomicInteger();
    //    Map<Class<?>, AtomicInteger> counts = new HashMap<>();
    //    state.getBackingNode().iterateAll(n ->
    //        counts.computeIfAbsent(n.getClass(), __ -> new AtomicInteger()).incrementAndGet());
    //    counts.entrySet().forEach(e -> {
    //      System.out.println(e.getKey().getSimpleName() + ": " + e.getValue());
    //    });
    Bytes serializeBytes = SimpleOffsetSerializer.serialize(state);
    System.out.println("Serialized size: " + serializeBytes.size());

    return state.getBackingNode();
  }

  @Test
  void b() {
    DataStructureUtil dataStructureUtil = new DataStructureUtil();
    BeaconState state0 = dataStructureUtil.randomBeaconState(4);
    BeaconState state = BeaconState.getSSZType().createFromBackingNode(state0.getBackingNode());
    Bytes32 treeRoot = state.getValidators().hash_tree_root();
    Validator validator0 = state.getValidators().get(0);
    Validator validator1 = state.getValidators().get(1);

    //    System.out.println(state);
    System.out.println(state.getBackingNode());
    System.out.println(state.getValidators().get(0));
    System.out.println(state.getValidators().get(0).getBackingNode());
    System.out.println(state.getValidators().get(1));
    System.out.println(state.getValidators().get(1).getBackingNode());
    System.out.println();
  }
}
