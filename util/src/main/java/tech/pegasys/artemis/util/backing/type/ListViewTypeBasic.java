package tech.pegasys.artemis.util.backing.type;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.MutableListView;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.CommitImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.view.BasicListViews;
import tech.pegasys.artemis.util.backing.view.BasicListViews.BytesListView;
import tech.pegasys.artemis.util.backing.view.BasicListViews.UInt64ListView;

public abstract class ListViewTypeBasic<C, L extends MutableListView<C>>  extends ListViewType<C, L> {

  public ListViewTypeBasic(int maxLength, int bitsPerElement) {
    super(maxLength, bitsPerElement);
  }

  @Override
  public L createDefault() {
    return createFromTreeNode(new CommitImpl(
        TreeNodeImpl.createZeroTree(treeDepth(), new RootImpl(Bytes32.ZERO)),
        new RootImpl(Bytes32.ZERO)));
  }

  private int maxChunks() {
    return (getMaxLength() * getBitsPerElement() - 1) / 256 + 1;
  }

  private int treeDepth() {
    return Integer.bitCount(nextPowerOf2(maxChunks()) - 1);
  }

  private static int nextPowerOf2(int x) {
    return x <= 1 ? 1 : Integer.highestOneBit(x - 1) << 1;
  }

  public static class BytesListType extends ListViewTypeBasic<Byte, BasicListViews.BytesListView> {

    public BytesListType(int maxLength) {
      super(maxLength, 8);
    }

    @Override
    public BytesListView createFromTreeNode(TreeNode node) {
      return new BytesListView(this, node);
    }
  }

  public static class UInt64ListType extends ListViewTypeBasic<UnsignedLong, BasicListViews.UInt64ListView> {

    public UInt64ListType(int maxLength) {
      super(maxLength, 64);
    }

    @Override
    public UInt64ListView createFromTreeNode(TreeNode node) {
      return new UInt64ListView(this, node);
    }
  }
}
