package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.MutableListView;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewType;

public abstract class PackedListView<C> implements MutableListView<C> {
  private final ListViewType<C, ? extends PackedListView<C>> type;
  private Commit backingNode;

  public PackedListView(ListViewType<C, PackedListView<C>> type) {
    this.type = type;
  }

  public PackedListView(
      ListViewType<C, ? extends PackedListView<C>> type,
      TreeNode backingNode) {
    this.type = type;
    this.backingNode = (Commit) backingNode;
  }

  abstract C decode(Bytes32 chunk, int internalIndex);
  abstract Bytes32 encode(Bytes32 originalChunk, int internalIndex, C value);

  @Override
  public void set(int index, C value) {
    checkArgument((index >= 0 && index < size()) || (index == size() && index < maxSize()),
        "Index out of bounds: %s, size=%s", index, size());

    if (index == size()) {
      backingNode = (Commit) backingNode.set(0b11,
          new RootImpl(Bytes32.rightPad(Bytes.ofUnsignedInt(index + 1, ByteOrder.LITTLE_ENDIAN))));
    }
    Commit newValuesNode = updateNode(index / type.getElementsPerNode(),
        oldBytes -> new RootImpl(
            encode(oldBytes.hashTreeRoot(), index % type.getElementsPerNode(), value)));
    backingNode = (Commit) backingNode.set(0b10, newValuesNode);
  }

  @Override
  public C get(int index) {
    checkArgument(index >= 0 && index < size(),
        "Index out of bounds: %s, size=%s", index, size());
    TreeNode node = getNode(index / type.getElementsPerNode());
    return decode(node.hashTreeRoot(), index % type.getElementsPerNode());
  }

  private Commit updateNode(int listIndex, Function<TreeNode, TreeNode> nodeUpdater) {
    return (Commit) backingNode.left().update(elementsTreeWidth() + listIndex, nodeUpdater);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.left().get(elementsTreeWidth() + listIndex);
  }

  @Override
  public int maxSize() {
    return type.getMaxLength();
  }

  private int maxChunks() {
    return (maxSize() * type.getBitsPerElement() - 1) / 256 + 1;
  }

  @Override
  public int size() {
    Bytes32 lengthBytes = backingNode.right().hashTreeRoot();
    return lengthBytes.slice(0, 4).toInt(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public ViewType<? extends View> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  private int elementsTreeWidth() {
    return nextPowerOf2(maxChunks());
  }

  private int treeDepth() {
    return Integer.bitCount(nextPowerOf2(maxChunks()) - 1);
  }

  private static int nextPowerOf2(int x) {
    return x <= 1 ? 1 : Integer.highestOneBit(x - 1) << 1;
  }
}
