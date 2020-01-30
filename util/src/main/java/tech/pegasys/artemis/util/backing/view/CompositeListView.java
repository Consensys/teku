package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.CommitImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewTypeComposite;

public class CompositeListView<C extends View> extends AbstractListView<C> {

  public static <C extends View> CompositeListView<C> createDefault(
      ListViewTypeComposite<C> type) {
    return new CompositeListView<>(
        type,
        new CommitImpl(TreeNodeImpl.createZeroTree(type.treeDepth(),
            type.getElementType().createDefault().getBackingNode()),
            new RootImpl(Bytes32.ZERO)));
  }

  public static <C extends View> CompositeListView<C> createFromTreeNode(ListViewTypeComposite<C> type, TreeNode listRootNode) {
    checkArgument(listRootNode instanceof Commit,
        "Expected Commit node for ListView: ", listRootNode);
    return new CompositeListView<C>(type, (Commit) listRootNode);
  }

  private CompositeListView(ListViewTypeComposite<C> type, Commit backingNode) {
    super(type, backingNode);
  }

  @Override
  protected C fromTreeNode(TreeNode node, int index) {
    return getType().getElementType().createFromTreeNode(node);
  }

  @Override
  protected TreeNode updateTreeNode(TreeNode srcNode, int index, C newValue) {
    return newValue.getBackingNode();
  }

  @Override
  @SuppressWarnings("unchecked")
  public ListViewTypeComposite<C> getType() {
    return (ListViewTypeComposite<C>) super.getType();
  }
}
