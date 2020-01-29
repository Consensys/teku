package tech.pegasys.artemis.util.backing;

public interface ViewType<V extends View> {

  V createDefault();

  V createFromTreeNode(TreeNode node);
}
