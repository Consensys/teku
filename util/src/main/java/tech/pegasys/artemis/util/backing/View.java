package tech.pegasys.artemis.util.backing;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.tree.TreeNode;

public interface View {

  ViewType<? extends View> getType();

  TreeNode getBackingNode();

  default Bytes32 hashTreeRoot() {
    return getBackingNode().hashTreeRoot();
  }
}
