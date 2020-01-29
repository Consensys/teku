package tech.pegasys.artemis.util.backing;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.ListView;

public class ListViewTest {

  static ViewType<TestView> testType = new ViewType<>() {

    @Override
    public TestView createDefault() {
      return new TestView(0);
    }

    @Override
    public TestView createFromTreeNode(TreeNode node) {
      return new TestView(node);
    }
  };

  static class TestView implements View {
    TreeNode node;
    public final int v;

    public TestView(int v) {
      this.v = v;
    }

    public TestView(TreeNode node) {
      this.node = node;
      this.v = node.hashTreeRoot().toInt();
    }

    @Override
    public ViewType<TestView> getType() {
      return testType;
    }

    @Override
    public TreeNode getBackingNode() {
      if (node == null) {
        node = new RootImpl(Bytes32.leftPad(Bytes.ofUnsignedInt(v)));
      }
      return node;
    }
  }

  @Test
  public void simpleTest1() {
    ListViewType<TestView> listType = new ListViewType<>(3, testType);
    ListView<TestView> list = listType.createDefault();
    TreeNode n0 = list.getBackingNode();
    list.set(0, new TestView(0x111));
    TreeNode n1 = list.getBackingNode();
    list.set(1, new TestView(0x222));
    TreeNode n2 = list.getBackingNode();
    list.set(2, new TestView(0x333));
    TreeNode n3 = list.getBackingNode();
    list.set(0, new TestView(0x444));
    TreeNode n4 = list.getBackingNode();
    System.out.println(n0);
    System.out.println(n1);
    System.out.println(n2);
    System.out.println(n3);
    System.out.println(n4);
  }

}
