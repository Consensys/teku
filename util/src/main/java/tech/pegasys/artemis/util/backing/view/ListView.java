package tech.pegasys.artemis.util.backing.view;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.TreeNode;
import tech.pegasys.artemis.util.backing.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.View;
import tech.pegasys.artemis.util.backing.ViewType;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.CommitImpl;
import tech.pegasys.artemis.util.backing.tree.TreeNodeImpl.RootImpl;
import tech.pegasys.artemis.util.backing.type.ListViewTypeComposite;

public class ListView<C extends View> implements CompositeListView<C> {

  private final ListViewTypeComposite<C> type;
  private Commit backingNode;

  public static <C extends View> ListView<C> createDefault(
      ListViewTypeComposite<C> type,
      TreeNode fillWith) {
    return new ListView<>(
        type,
        new CommitImpl(TreeNodeImpl.createZeroTree(treeDepth(type.getMaxLength()), fillWith),
            new RootImpl(Bytes32.ZERO)));
  }

  public static <C extends View> ListView<C> createFromTreeNode(ListViewTypeComposite<C> type, TreeNode listRootNode) {
    checkArgument(listRootNode instanceof Commit,
        "Expected Commit node for ListView: ", listRootNode);
    return new ListView<C>(type, (Commit) listRootNode);
  }

  private ListView(ListViewTypeComposite<C> type, Commit backingNode) {
    this.type = type;
    this.backingNode = backingNode;
  }

  @Override
  public ViewType<ListView<C>> getType() {
    return type;
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  public int size() {
    Bytes32 lengthBytes = backingNode.right().hashTreeRoot();
    return lengthBytes.slice(0, 4).toInt(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public int maxSize() {
    return type.getMaxLength();
  }

  @Override
  public C get(int index) {
    checkArgument(index >= 0 && index < size(),
        "Index out of bounds: %s, size=%s", index, size());
    TreeNode node = getNode(index);
    return type.getElementType().createFromTreeNode(node);
  }

  @Override
  public void set(int index, C value) {
    checkArgument((index >= 0 && index < size()) || (index == size() && index < maxSize()),
        "Index out of bounds: %s, size=%s", index, size());

    if (index == size()) {
      backingNode = (Commit) backingNode.set(0b11,
          new RootImpl(Bytes32.rightPad(Bytes.ofUnsignedInt(index + 1, ByteOrder.LITTLE_ENDIAN))));
    }
    Commit newValuesNode = setNode(index, value.getBackingNode());
    backingNode = (Commit) backingNode.set(0b10, newValuesNode);
  }

  private Commit setNode(int listIndex, TreeNode newNode) {
    return (Commit) backingNode.left().set(elementsTreeWidth() + listIndex, newNode);
  }

  private TreeNode getNode(int listIndex) {
    return backingNode.left().get(elementsTreeWidth() + listIndex);
  }


  private int elementsTreeWidth() {
    return nextPowerOf2(maxSize());
  }

  private static int treeDepth(int maxSize) {
    return Integer.bitCount(nextPowerOf2(maxSize) - 1);
  }

  private static int nextPowerOf2(int x) {
    return x <= 1 ? 1 : Integer.highestOneBit(x - 1) << 1;
  }
}
