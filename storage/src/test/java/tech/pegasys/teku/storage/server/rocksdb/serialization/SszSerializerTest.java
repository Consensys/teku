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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;

public class SszSerializerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final SszSerializer<SignedBeaconBlock> blockSerializer =
      new SszSerializer<>(SignedBeaconBlock.class);
  private final SszSerializer<BeaconState> stateSerializer =
      new SszSerializer<>(BeaconStateImpl.class);
  private final SszSerializer<Checkpoint> checkpointSerializer =
      new SszSerializer<>(Checkpoint.class);

  @Test
  public void roundTrip_block() {
    final SignedBeaconBlock value = dataStructureUtil.randomSignedBeaconBlock(11);
    final byte[] bytes = blockSerializer.serialize(value);
    final SignedBeaconBlock deserialized = blockSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  void a() throws InterruptedException {
    BLSPublicKey pubKey = BLSPublicKey.random(1);
    DataStructureUtil dataStructureUtil = new DataStructureUtil().withPubKeyGenerator(() -> pubKey);
    System.out.println("Generating state...");
    BeaconState state = dataStructureUtil.randomBeaconState(100_000);
    //    System.out.println("Counting nodes...");
    //    AtomicInteger branchCount = new AtomicInteger();
    //    AtomicInteger leafCount = new AtomicInteger();
    //    traverse(state.getBackingNode(), n -> {
    //      if (n instanceof BranchNode) {
    //        branchCount.incrementAndGet();
    //      } else {
    //        leafCount.incrementAndGet();
    //      }
    //    });
    //    System.out.println("Branch: " + branchCount + ", leaf: " + leafCount);
    Validator validator = state.getValidators().get(0);
    TreeNode validatorTree = validator.getBackingNode();
    System.out.println(TreeUtil.dumpBinaryTree(validatorTree));
    Bytes sszOld = SimpleOffsetSerializer.serialize(validator);
    Bytes sszNew = validator.sszSerialize();
    System.out.println("Validator SSZ old: " + sszOld);
    System.out.println("Validator SSZ new: " + sszNew);

    SSZBackingList<UInt64, Validator> validators = (SSZBackingList) state.getValidators();
    SSZBackingList<UInt64, UInt64View> balances = (SSZBackingList) state.getBalances();
    Bytes validatorsSSZ = validators.getDelegate().sszSerialize();
    Bytes balancesSSZ = balances.getDelegate().sszSerialize();

    Bytes sszStateOld = SimpleOffsetSerializer.serialize(state);
    Bytes sszStateNew = state.sszSerialize();

    assertThat(sszStateNew.toArrayUnsafe()).isEqualTo(sszStateOld.toArrayUnsafe());
    /*
          for (int i = 0; i < sszStateNew.size(); i++) {
            if (sszStateOld.get(i) != sszStateNew.get(i)) {
              System.out.println("Differ at " + i);
              System.out.println("Before: " + sszStateOld.slice(i - 128, 128));
              System.out.println("After old: " + sszStateOld.slice(i, 256));
              System.out.println("After new: " + sszStateNew.slice(i, 256));
            }
          }
    */

    System.out.println("Root: " + state.hashTreeRoot());

    //    Thread.sleep(100000000000L);

    while (true) {
      long s = System.currentTimeMillis();
      for (int i = 0; i < 10; i++) {
        //        SimpleOffsetSerializer.serialize(state).toArrayUnsafe();
        byte[] bytes = state.sszSerialize().toArrayUnsafe();
        //        System.out.println(bytes.length);
      }
      System.out.println(System.currentTimeMillis() - s);
    }

    //    Bytes serializeBytesOld = SimpleOffsetSerializer.serialize(state);
    //    Bytes serializeBytesNew = state.sszSerialize();
    //    System.out.println("Serialized size: " + serializeBytesOld.size());
  }

  void traverse(TreeNode node, Consumer<TreeNode> visitor) {
    if (node == null) return;
    visitor.accept(node);
    if (node instanceof BranchNode) {
      BranchNode branchNode = (BranchNode) node;
      if (branchNode.isZero()) return;

      traverse(branchNode.left(), visitor);
      traverse(branchNode.right(), visitor);
    }
  }

  @Test
  public void roundTrip_state() {
    final BeaconState value = dataStructureUtil.randomBeaconState(11);
    final byte[] bytes = stateSerializer.serialize(value);
    final BeaconState deserialized = stateSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_checkpoint() {
    final Checkpoint value = dataStructureUtil.randomCheckpoint();
    final byte[] bytes = checkpointSerializer.serialize(value);
    final Checkpoint deserialized = checkpointSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }
}
