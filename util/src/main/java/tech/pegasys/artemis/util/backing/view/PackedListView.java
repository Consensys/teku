package tech.pegasys.artemis.util.backing.view;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewType;

public abstract class PackedListView<C> extends AbstractListView<C> {

  public PackedListView(ListViewType<C, ? extends PackedListView<C>> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  protected C fromTreeNode(TreeNode node, int index) {
    return decode(node.hashTreeRoot(), index % type.getElementsPerNode());
  }

  @Override
  protected TreeNode updateTreeNode(TreeNode srcNode, int index, C newValue) {
    return new RootImpl(
        encode(srcNode.hashTreeRoot(), index % type.getElementsPerNode(), newValue));
  }

  abstract C decode(Bytes32 chunk, int internalIndex);
  abstract Bytes32 encode(Bytes32 originalChunk, int internalIndex, C value);
}
