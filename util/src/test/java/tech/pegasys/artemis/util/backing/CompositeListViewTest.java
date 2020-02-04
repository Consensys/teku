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

package tech.pegasys.artemis.util.backing;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewType;

public class CompositeListViewTest {

  static ViewType<TestView> testType =
      new ViewType<>() {

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
      this.v = node.hashTreeRoot().trimLeadingZeros().toInt();
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
    ListViewType<TestView> listType = new ListViewType<>(testType, 3);
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

    Assertions.assertEquals(0, listType.createFromTreeNode(n0).size());
    Assertions.assertEquals(1, listType.createFromTreeNode(n1).size());
    Assertions.assertEquals(0x111, listType.createFromTreeNode(n1).get(0).v);
    Assertions.assertEquals(2, listType.createFromTreeNode(n2).size());
    Assertions.assertEquals(0x111, listType.createFromTreeNode(n2).get(0).v);
    Assertions.assertEquals(0x222, listType.createFromTreeNode(n2).get(1).v);
    Assertions.assertEquals(3, listType.createFromTreeNode(n3).size());
    Assertions.assertEquals(0x111, listType.createFromTreeNode(n3).get(0).v);
    Assertions.assertEquals(0x222, listType.createFromTreeNode(n3).get(1).v);
    Assertions.assertEquals(0x333, listType.createFromTreeNode(n3).get(2).v);
    Assertions.assertEquals(3, listType.createFromTreeNode(n4).size());
    Assertions.assertEquals(0x444, listType.createFromTreeNode(n4).get(0).v);
    Assertions.assertEquals(0x222, listType.createFromTreeNode(n4).get(1).v);
    Assertions.assertEquals(0x333, listType.createFromTreeNode(n4).get(2).v);
  }
}
