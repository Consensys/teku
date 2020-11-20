package tech.pegasys.teku.ssz.backing.tree;

public interface TreeVisitor {

  boolean visit(TreeNode node, long generalizedIndex);
}
