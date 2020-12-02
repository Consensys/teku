package tech.pegasys.teku.ssz.backing.tree;

import org.apache.tuweni.bytes.Bytes;

public interface LeadDataNode extends TreeNode {

  Bytes getData();
}
