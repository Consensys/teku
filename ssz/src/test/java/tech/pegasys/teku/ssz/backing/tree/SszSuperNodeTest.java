package tech.pegasys.teku.ssz.backing.tree;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.TestUtil.TestContainer;
import tech.pegasys.teku.ssz.TestUtil.TestSubContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.tree.TreeNode.BranchNode;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.TypeHints;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszSuperNodeTest {

  @Test
  void test1() {
    ViewType listElementType = TestContainer.TYPE;
    VectorViewType<TestContainer> superNodeType = new VectorViewType<>(
        listElementType, 4);
    SszNodeTemplate.createFromType(superNodeType);

    ListViewType<TestContainer> lt1 = new ListViewType<>(listElementType, 10, TypeHints.sszSuperLeaf(2));
    ListViewType<TestContainer> lt2 = new ListViewType<>(listElementType, 10);

    TreeNode defTree1 = lt1.getDefaultTree();
    TreeNode defTree2 = lt2.getDefaultTree();

    assertThat(defTree1.hashTreeRoot()).isEqualTo(defTree2.hashTreeRoot());

    ListViewRead<TestContainer> lr1_1 = lt1.getDefault();
    ListViewRead<TestContainer> lr2_1 = lt2.getDefault();

    Bytes32 bytes32 = Bytes32
        .fromHexString("0x2222222222222222222222222222222222222222222222222222222222222222");
    TestSubContainer subContainer = new TestSubContainer(UInt64.valueOf(0x111111), bytes32);
    TestContainer testContainer = new TestContainer(subContainer, UInt64.valueOf(0x333333));

    ListViewWrite<TestContainer> lw1_1 = lr1_1.createWritableCopy();
    ListViewWrite<TestContainer> lw2_1 = lr2_1.createWritableCopy();

    lw1_1.append(testContainer);
    lw2_1.append(testContainer);

    ListViewRead<TestContainer> lr1_2 = lw1_1.commitChanges();
    ListViewRead<TestContainer> lr2_2 = lw2_1.commitChanges();

    assertThat(lr1_2.hashTreeRoot()).isEqualTo(lr2_2.hashTreeRoot());
  }

}
