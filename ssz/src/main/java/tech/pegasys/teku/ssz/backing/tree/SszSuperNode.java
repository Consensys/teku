package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetChildIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetRelativeGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxIsSelf;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.ssz.backing.tree.SszNodeTemplate.Location;

public class SszSuperNode implements TreeNode {
  private static final TreeNode DEFAULT_NODE = TreeUtil.EMPTY_LEAF;

  private final int depth;
  private final SszNodeTemplate elementTemplate;
  private final Bytes ssz;
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calcHashTreeRoot);


  public SszSuperNode(int depth, SszNodeTemplate elementTemplate, Bytes ssz) {
    this.depth = depth;
    this.elementTemplate = elementTemplate;
    this.ssz = ssz;
    checkArgument(ssz.size() % elementTemplate.getSszLength() == 0);
    checkArgument(getElementsCount() <= getMaxElements());
  }

  private int getMaxElements() {
    return 1 << depth;
  }

  private int getElementsCount() {
    return ssz.size() / elementTemplate.getSszLength();
  }

  @Override
  public Bytes32 hashTreeRoot() {
    return hashTreeRoot.get();
  }

  private Bytes32 calcHashTreeRoot() {
    return hashTreeRoot(0, 0);
  }

  private Bytes32 hashTreeRoot(int curDepth, int offset) {
    if (curDepth == depth) {
      if (offset < ssz.size()) {
        return elementTemplate.calculateHashTreeRoot(ssz, offset);
      } else {
        return DEFAULT_NODE.hashTreeRoot();
      }
    } else {
      return Hash.sha2_256(Bytes.wrap(
          hashTreeRoot(curDepth + 1, offset),
          hashTreeRoot(curDepth + 1,
              offset + elementTemplate.getSszLength() * (1 << (depth - curDepth) - 1)
          )));
    }
  }

  @NotNull
  @Override
  public TreeNode get(long generalizedIndex) {
    if (gIdxIsSelf(generalizedIndex)) {
      return this;
    }
    int childIndex = gIdxGetChildIndex(generalizedIndex, depth);
    int childOffset = childIndex * elementTemplate.getSszLength();
    checkArgument(childOffset < ssz.size(), "Invalid index");
    long relativeGIndex = gIdxGetRelativeGIndex(generalizedIndex, depth);
    Location leafPos = elementTemplate.getNodeSszLocation(relativeGIndex);
    return TreeNode.createLeafNode(ssz.slice(childOffset + leafPos.offset, leafPos.length));
  }

  @Override
  public boolean iterate(TreeVisitor visitor, long thisGeneralizedIndex,
      long startGeneralizedIndex) {
    if (gIdxCompare(thisGeneralizedIndex, startGeneralizedIndex) == NodeRelation.Left) {
      return true;
    } else {
      return visitor.visit(this, thisGeneralizedIndex);
    }
  }

  @Override
  public TreeNode updated(long generalizedIndex, TreeNode node) {
    // sub-optimal update implementation
    // implement other method to optimize
    int childIndex = gIdxGetChildIndex(generalizedIndex, depth);
    int childOffset = childIndex * elementTemplate.getSszLength();
    checkArgument(childOffset <= ssz.size(), "Invalid index");
    long relativeGIndex = gIdxGetRelativeGIndex(generalizedIndex, depth);
    Bytes sszNewSize = childOffset < ssz.size() ? ssz
        : Bytes.wrap(ssz, Bytes.wrap(new byte[elementTemplate.getSszLength()]));
    MutableBytes mutableCopy = sszNewSize.mutableCopy();
    MutableBytes childMutableSlice = mutableCopy
        .mutableSlice(childOffset, elementTemplate.getSszLength());
    elementTemplate.update(relativeGIndex, node, childMutableSlice);
    return new SszSuperNode(depth, elementTemplate, mutableCopy);
  }

  public Bytes getSsz() {
    return ssz;
  }

  @Override
  public String toString() {
    return "SszSuperNode{" + IntStream.range(0, getElementsCount())
        .mapToObj(i -> ssz.slice(i, elementTemplate.getSszLength()).toString())
        .collect(Collectors.joining(", ")) + "}";
  }
}
