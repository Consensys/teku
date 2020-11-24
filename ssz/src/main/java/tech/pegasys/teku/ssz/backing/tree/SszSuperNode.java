package tech.pegasys.teku.ssz.backing.tree;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;
import tech.pegasys.teku.ssz.backing.tree.SszNodeTemplate.Location;

public class SszSuperNode implements TreeNode {

  private final SszNodeTemplate template;
  private final Bytes ssz;
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calcHashTreeRoot);

  public SszSuperNode(SszNodeTemplate template, Bytes ssz) {
    this.template = template;
    this.ssz = ssz;
  }

  @Override
  public Bytes32 hashTreeRoot() {
    return hashTreeRoot.get();
  }

  private Bytes32 calcHashTreeRoot() {
    return template.calculateHashTreeRoot(ssz, 0);
  }

  @NotNull
  @Override
  public TreeNode get(long generalizedIndex) {
    Location leafPos = template.getNodeSszLocation(generalizedIndex);
    return TreeNode.createLeafNode(ssz.slice(leafPos.offset, leafPos.length));
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
    Location leafPos = template.getNodeSszLocation(generalizedIndex);
    List<Bytes> nodeSsz = nodeSsz(node);
    MutableBytes mutableBytes = ssz.mutableCopy();
    int off = 0;
    for (int i = 0; i < nodeSsz.size(); i++) {
      Bytes newSszChunk = nodeSsz.get(i);
      newSszChunk.copyTo(mutableBytes, leafPos.offset + off);
      off += newSszChunk.size();
    }
    checkArgument(off == leafPos.length);
    return new SszSuperNode(template, mutableBytes);
  }

  private List<Bytes> nodeSsz(TreeNode node) {
    List<Bytes> sszBytes = new ArrayList<>();
    TreeUtil.iterateLeavesData(node, LEFTMOST_G_INDEX, RIGHTMOST_G_INDEX, sszBytes::add);
    return sszBytes;
  }

  public Bytes getSsz() {
    return ssz;
  }
}
