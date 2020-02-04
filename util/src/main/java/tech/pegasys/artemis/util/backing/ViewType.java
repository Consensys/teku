package tech.pegasys.artemis.util.backing;

import tech.pegasys.artemis.util.backing.tree.TreeNode;

public interface ViewType<V extends View> {

  V createDefault();

  V createFromTreeNode(TreeNode node);

  default V createFromTreeNode(TreeNode node, int internalIndex) {
    return createFromTreeNode(node);
  }

  default TreeNode updateTreeNode(TreeNode srcNode, int internalIndex, V newValue) {
    return newValue.getBackingNode();
  }

  default int getBitsSize() {
    return 256;
  }
}
