package tech.pegasys.teku.ssz.backing.type;

import java.util.List;

public class TypeHints {
  public final boolean superLeafNode;
  public final List<Integer> superBranchDepths;

  public static TypeHints none() {
    return new TypeHints(false, null);
  }

  public static TypeHints superLeaf() {
    return new TypeHints(true, null);
  }

  public static TypeHints superBranch(List<Integer> superBranchDepths) {
    return new TypeHints(false, superBranchDepths);
  }

  private TypeHints(boolean superLeafNode, List<Integer> superBranchDepths) {
    this.superLeafNode = superLeafNode;
    this.superBranchDepths = superBranchDepths;
  }

  public boolean isSuperLeafNode() {
    return superLeafNode;
  }

  public List<Integer> getSuperBranchDepths() {
    return superBranchDepths;
  }

  public boolean isSuperBranchNodes() {
    return superBranchDepths != null;
  }
}
