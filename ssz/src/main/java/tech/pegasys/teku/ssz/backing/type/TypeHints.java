package tech.pegasys.teku.ssz.backing.type;

public class TypeHints {
  public final boolean superLeafNode;

  public TypeHints() {
    this(false);
  }

  public TypeHints(boolean superLeafNode) {
    this.superLeafNode = superLeafNode;
  }

  public boolean isSuperLeafNode() {
    return superLeafNode;
  }
}
