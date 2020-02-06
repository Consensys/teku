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

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Root;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.PackedUnsignedLongView;
import tech.pegasys.artemis.util.backing.view.BasicViews.UnsignedLongView;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;

public class ContainerViewTest {

  public interface TestSubContainerRead<C extends ViewRead> extends ContainerViewRead<C> {

    UnsignedLong getLong1();

    UnsignedLong getLong2();

    @Override
    TestSubContainerWrite createWritableCopy();
  }

  public interface TestSubContainerWrite extends TestSubContainerRead<ViewRead>, ContainerViewWrite<ViewRead> {

    void setLong1(UnsignedLong val);

    void setLong2(UnsignedLong val);

    @Override
    TestSubContainerRead<ViewRead> commitChanges();
  }

  public interface TestContainerRead extends ContainerViewRead<ViewRead>{

      UnsignedLong getLong1();

      UnsignedLong getLong2();

      ListViewRead<PackedUnsignedLongView> getList1();

      ListViewRead<? extends TestSubContainerRead> getList2();

    @Override
    TestContainerWrite createWritableCopy();
  }

    public interface TestContainerWrite extends TestContainerRead,
        ContainerViewWriteRef<ViewRead, ViewWrite> {

      void setLong1(UnsignedLong val);

      void setLong2(UnsignedLong val);

      ListViewWrite<PackedUnsignedLongView> getList1();

      ListViewWrite<TestSubContainerRead> getList2();

      @Override
      TestContainerRead commitChanges();
    }

  public static class TestSubContainerImpl extends ContainerViewImpl {
    public static final ContainerViewType<TestSubContainerImpl> TYPE =
        new ContainerViewType<>(
            Arrays.asList(BasicViewTypes.UNSIGNED_LONG_TYPE, BasicViewTypes.UNSIGNED_LONG_TYPE),
            TestSubContainerImpl::new);

    private TestSubContainerImpl(
        ContainerViewType<TestSubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public UnsignedLong getLong1() {
      return ((UnsignedLongView) get(0)).get();
    }

    //    @Override
    public UnsignedLong getLong2() {
      return ((UnsignedLongView) get(1)).get();
    }

    public void setLong1(UnsignedLong val) {
      set(0, new UnsignedLongView(val));
    }

    //    @Override
    public void setLong2(UnsignedLong val) {
      set(1, new UnsignedLongView(val));
    }
  }

  public static class TestContainerImpl
      extends ContainerViewImpl implements TestContainerWrite {
    public static final ContainerViewType<TestContainerImpl> TYPE =
        new ContainerViewType<>(
            Arrays.asList(
                BasicViewTypes.UNSIGNED_LONG_TYPE,
                BasicViewTypes.UNSIGNED_LONG_TYPE,
                TestSubContainerImpl.TYPE,
                new ListViewType<>(BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE, 10),
                new ListViewType<>(TestSubContainerImpl.TYPE, 2)),
            TestContainerImpl::new);

    public TestContainerImpl(ContainerViewType<TestContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    //    @Override
    public UnsignedLong getLong1() {
      return ((UnsignedLongView) get(0)).get();
    }

    //    @Override
    public UnsignedLong getLong2() {
      return ((UnsignedLongView) get(1)).get();
    }

    public TestSubContainerImpl getContainer() {
      return (TestSubContainerImpl) get(2);
    }

    //    @Override
    @SuppressWarnings("unchecked")
    public ListViewWrite<PackedUnsignedLongView> getList1() {
      return (ListViewWrite<PackedUnsignedLongView>) get(3);
    }

    @SuppressWarnings("unchecked")
    public ListViewWrite<TestSubContainerRead> getList2() {
      return (ListViewWrite<TestSubContainerRead>) get(4);
    }

    //    @Override
    public void setLong1(UnsignedLong val) {
      set(0, new UnsignedLongView(val));
    }

    //    @Override
    public void setLong2(UnsignedLong val) {
      set(1, new UnsignedLongView(val));
    }

    public void setContainer(TestSubContainerImpl val) {
      set(2, val);
    }

    //    @Override
    public void setList1(ListViewWrite<PackedUnsignedLongView> val) {
      set(3, val);
    }

    public void setList2(ListViewWrite<TestSubContainerImpl> val) {
      set(4, val);
    }

    @Override
    public TestContainerWrite createWritableCopy() {
      return this;
    }

    @Override
    public TestContainerRead commitChanges() {
      return this;
    }
  }

  @Test
  public void readWriteContainerTest() {
    TestContainerRead c1 = TestContainerImpl.TYPE.createDefault();
    TestContainerWrite c1w = c1.createWritableCopy();
    c1w.setLong1(UnsignedLong.valueOf(0x111));
    TestContainerRead c1r = c1w.commitChanges();
    dumpBinaryTree(c1r.getBackingNode());
//    c1w.getList2().append();
  }

  @Test
  public void simpleContainerTest() {
    TestContainerImpl c1 = TestContainerImpl.TYPE.createDefault();
    c1.setLong1(UnsignedLong.valueOf(0x111));
    c1.setLong2(UnsignedLong.valueOf(0x222));
    ListViewWrite<PackedUnsignedLongView> list1 = c1.getList1();
    list1.append(PackedUnsignedLongView.fromLong(0x333));
    c1.setList1(list1);
    dumpBinaryTree(c1.getBackingNode());
  }

  public static void dumpBinaryTree(TreeNode node) {
    dumpBinaryTreeRec(node, "", false);
  }

  private static void dumpBinaryTreeRec(TreeNode node, String prefix, boolean printCommit) {
    if (node instanceof Root) {
      Root rootNode = (Root) node;
      System.out.println(prefix + rootNode);
    } else {
      Commit commitNode = (Commit) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + commitNode;
      }
      if (commitNode.left() instanceof Root) {
        System.out.println(prefix + "├─" + commitNode.left());
      } else {
        System.out.println(prefix + s);
        dumpBinaryTreeRec(commitNode.left(), prefix + "│ ", printCommit);
      }
      if (commitNode.right() instanceof Root) {
        System.out.println(prefix + "└─" + commitNode.right());
      } else {
        System.out.println(prefix + "└─┐");
        dumpBinaryTreeRec(commitNode.right(), prefix + "  ", printCommit);
      }
    }
  }
}
