package tech.pegasys.artemis.util.backing;

public interface View {

  ViewType<? extends View> getType();

  TreeNode getBackingNode();
}
