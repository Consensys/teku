package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Function;
import tech.pegasys.artemis.util.backing.VectorView;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.type.VectorViewType;

public class VectorViewImpl<C extends View> implements VectorView<C> {
  protected final VectorViewType<C> type;
  protected TreeNode backingNode;

  public VectorViewImpl(
      VectorViewType<C> type,
      TreeNode backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  @Override
  public void set(int index, C value) {
    checkArgument(index >= 0 && index < type.getMaxLength(),
        "Index out of bounds: %s, size=%s", index, size());

    backingNode = updateNode(index / type.getElementsPerChunk(),
        oldBytes -> type.getElementType()
            .updateTreeNode(oldBytes, index % type.getElementsPerChunk(), value));
  }

  @Override
  public C get(int index) {
    checkArgument(index >= 0 && index < type.getMaxLength(),
        "Index out of bounds: %s, size=%s", index, size());
    TreeNode node = getNode(index / type.getElementsPerChunk());
    return type.getElementType().createFromTreeNode(node, index % type.getElementsPerChunk());
  }

  private Commit updateNode(int listIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    return (Commit) backingNode.update(type.treeWidth() + listIndex, nodeUpdater);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.get(type.treeWidth() + listIndex);
  }

  @Override
  public VectorViewType<C> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }
}
