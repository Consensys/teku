package tech.pegasys.artemis.util.backing;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.PackedUnsignedLongView;
import tech.pegasys.artemis.util.backing.view.BasicViews.UnsignedLongView;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;

public class ContainerViewTest {

//  public interface TestContainerRead {
//
//    UnsignedLong getLong1();
//
//    UnsignedLong getLong2();
//
//    ListView<UnsignedLong> getList1();
//  }
//
//  public interface TestContainerWrite extends TestContainerRead {
//
//    void setLong1(UnsignedLong val);
//
//    void setLong2(UnsignedLong val);
//
//    void setList1(ListView<UnsignedLong> val);
//
//    default void updateList1(int index, Function<UnsignedLong, UnsignedLong> updater) {
//      getList1().update(index, updater);
//      setList1(getList1());
//    }
//  }

  public static class TestSubContainerImpl extends ContainerViewImpl {
    public static final ContainerViewType<TestSubContainerImpl> TYPE = new ContainerViewType<>(Arrays.asList(
        BasicViewTypes.UNSIGNED_LONG_TYPE,
        BasicViewTypes.UNSIGNED_LONG_TYPE
    ), TestSubContainerImpl::new);

    private TestSubContainerImpl(ContainerViewType<TestSubContainerImpl> type, TreeNode backingNode) {
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

  public static class TestContainerImpl extends ContainerViewImpl /*implements TestContainerWrite*/{
    public static final ContainerViewType<TestContainerImpl> TYPE = new ContainerViewType<>(Arrays.asList(
        BasicViewTypes.UNSIGNED_LONG_TYPE,
        BasicViewTypes.UNSIGNED_LONG_TYPE,
        TestSubContainerImpl.TYPE,
        new ListViewType<>(BasicViewTypes.PACKED_UNSIGNED_LONG_TYPE, 10),
        new ListViewType<>(TestSubContainerImpl.TYPE, 2)
    ), TestContainerImpl::new);

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
    public ListView<PackedUnsignedLongView> getList1() {
      return (ListView<PackedUnsignedLongView>) get(3);
    }

    public ListView<TestSubContainerImpl> getList2() {
      return (ListView<TestSubContainerImpl>) get(4);
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
    public void setList1(ListView<PackedUnsignedLongView> val) {
      set(3, val);
    }

    public void setList2(ListView<TestSubContainerImpl> val) {
      set(4, val);
    }
  }

  @Test
  public void simpleContainerTest() {
    TestContainerImpl c1 = TestContainerImpl.TYPE.createDefault();
    c1.setLong1(UnsignedLong.valueOf(0x111));
    c1.setLong2(UnsignedLong.valueOf(0x222));
    ListView<PackedUnsignedLongView> list1 = c1.getList1();
    list1.append(PackedUnsignedLongView.fromLong(0x333));
    c1.setList1(list1);
    Utils.dumpBinaryTree(c1.getBackingNode());
  }

}
