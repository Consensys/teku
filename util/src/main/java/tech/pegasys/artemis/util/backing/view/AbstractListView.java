package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.MutableListView;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewType;

public abstract class AbstractListView <C> implements MutableListView<C> {
  protected final ListViewType<C, ? extends AbstractListView<C>> type;
  protected Commit backingNode;

  public AbstractListView(
      ListViewType<C, ? extends AbstractListView<C>> type,
      TreeNode backingNode) {
    this.type = type;
    this.backingNode = (Commit) backingNode;
  }

  protected abstract C fromTreeNode(TreeNode node, int index);

  protected abstract TreeNode updateTreeNode(TreeNode srcNode, int index, C newValue);

  @Override
  public void set(int index, C value) {
    checkArgument((index >= 0 && index < size()) || (index == size() && index < maxSize()),
        "Index out of bounds: %s, size=%s", index, size());

    if (index == size()) {
      backingNode = (Commit) backingNode.set(0b11,
          new RootImpl(Bytes32.rightPad(Bytes.ofUnsignedInt(index + 1, ByteOrder.LITTLE_ENDIAN))));
    }
    Commit newValuesNode = updateNode(index / type.getElementsPerNode(),
        oldBytes -> updateTreeNode(oldBytes, index, value));
    backingNode = (Commit) backingNode.set(0b10, newValuesNode);
  }

  @Override
  public C get(int index) {
    checkArgument(index >= 0 && index < size(),
        "Index out of bounds: %s, size=%s", index, size());
    TreeNode node = getNode(index / type.getElementsPerNode());
    return fromTreeNode(node, index);
  }

  private Commit updateNode(int listIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    return (Commit) backingNode.left().update(type.treeWidth() + listIndex, nodeUpdater);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.left().get(type.treeWidth() + listIndex);
  }

  @Override
  public int maxSize() {
    return type.getMaxLength();
  }

  @Override
  public int size() {
    Bytes32 lengthBytes = backingNode.right().hashTreeRoot();
    return lengthBytes.slice(0, 4).toInt(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public ViewType<? extends View> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }
}
