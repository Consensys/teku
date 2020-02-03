package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Function;
import tech.pegasys.artemis.util.backing.CompositeViewType;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.VectorView;
import tech.pegasys.artemis.util.backing.View;

public abstract class AbstractVectorView<C> implements VectorView<C> {
  protected final CompositeViewType<? extends AbstractVectorView<C>> type;
  protected TreeNode backingNode;

  public AbstractVectorView(
      CompositeViewType<? extends AbstractVectorView<C>> type,
      TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  protected abstract C fromTreeNode(TreeNode node, int index);

  protected abstract TreeNode updateTreeNode(TreeNode srcNode, int index, C newValue);

  @Override
  public void set(int index, C value) {
    checkArgument(index >= 0 && index < type.getMaxLength(),
        "Index out of bounds: %s, size=%s", index, size());

    backingNode = updateNode(index / type.getElementsPerNode(),
        oldBytes -> updateTreeNode(oldBytes, index, value));
  }

  @Override
  public C get(int index) {
    checkArgument(index >= 0 && index < type.getMaxLength(),
        "Index out of bounds: %s, size=%s", index, size());
    TreeNode node = getNode(index / type.getElementsPerNode());
    return fromTreeNode(node, index);
  }

  private Commit updateNode(int listIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    return (Commit) backingNode.update(type.treeWidth() + listIndex, nodeUpdater);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.get(type.treeWidth() + listIndex);
  }

  @Override
  public CompositeViewType<? extends View> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }
}
