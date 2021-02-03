package tech.pegasys.teku.ssz.backing.type;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SSZDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public abstract class AbstractDelegateType<ListTypeT extends ViewRead>
    implements ViewType<ListTypeT> {

  private final ViewType<? super ListTypeT> delegate;

  public AbstractDelegateType(ViewType<? super ListTypeT> delegate) {
    this.delegate = delegate;
  }

  @Override
  public TreeNode getDefaultTree() {
    return delegate.getDefaultTree();
  }

  @Override
  public abstract ListTypeT createFromBackingNode(TreeNode node);

  @Override
  public ViewRead getDefault() {
    return delegate.getDefault();
  }

  @Override
  public int getBitsSize() {
    return delegate.getBitsSize();
  }

  @Override
  public ViewRead createFromBackingNode(TreeNode node,
      int internalIndex) {
    return delegate.createFromBackingNode(node, internalIndex);
  }

  @Override
  public TreeNode updateBackingNode(
      TreeNode srcNode, int internalIndex,
      ViewRead newValue) {
    return delegate.updateBackingNode(srcNode, internalIndex, newValue);
  }

  @Override
  public Bytes sszSerialize(ListTypeT view) {
    return delegate.sszSerialize(view);
  }

  @Override
  public int sszSerialize(ListTypeT view, SszWriter writer) {
    return delegate.sszSerialize(view, writer);
  }

  @Override
  public ListTypeT sszDeserialize(SszReader reader) throws SSZDeserializeException {
    return createFromBackingNode(delegate.sszDeserializeTree(reader));
  }

  @Override
  public ListTypeT sszDeserialize(Bytes ssz) throws SSZDeserializeException {
    return sszDeserialize(SszReader.fromBytes(ssz));
  }

  public static Bytes lengthToBytes(int length) {
    return SszType.lengthToBytes(length);
  }

  public static int bytesToLength(Bytes bytes) {
    return SszType.bytesToLength(bytes);
  }

  @Override
  public boolean isFixedSize() {
    return delegate.isFixedSize();
  }

  @Override
  public int getFixedPartSize() {
    return delegate.getFixedPartSize();
  }

  @Override
  public int getVariablePartSize(TreeNode node) {
    return delegate.getVariablePartSize(node);
  }

  @Override
  public int getSszSize(TreeNode node) {
    return delegate.getSszSize(node);
  }

  @Override
  public Bytes sszSerializeTree(
      TreeNode node) {
    return delegate.sszSerializeTree(node);
  }

  @Override
  public int sszSerializeTree(TreeNode node,
      SszWriter writer) {
    return delegate.sszSerializeTree(node, writer);
  }

  @Override
  public TreeNode sszDeserializeTree(
      SszReader reader) throws SSZDeserializeException {
    return delegate.sszDeserializeTree(reader);
  }

  @Override
  public SszLengthBounds getSszLengthBounds() {
    return delegate.getSszLengthBounds();
  }
}
