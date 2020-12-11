package tech.pegasys.teku.ssz.backing.tree;

import static tech.pegasys.teku.ssz.backing.tree.TreeUtil.treeDepth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TreeBuilder {

  private int depth;
  private List<TreeNode> firstElements = new ArrayList<>();
  private TreeNode defaultElement = LeafNode.EMPTY_LEAF;
  private TreeNode emptyNode = LeafNode.EMPTY_LEAF;
//  private SszNodeTemplate sszNodeTemplate;
//  private int superNodeDepth;

  public TreeBuilder depth(int depth) {
    this.depth = depth;
    return this;
  }

  public TreeBuilder maxNodes(long maxNodes) {
    this.depth = treeDepth(maxNodes);
    return this;
  }

  public TreeBuilder defaultElement(TreeNode defaultElement) {
    this.defaultElement = defaultElement;
    return this;
  }

//  public TreeBuilder sszTemplate(SszNodeTemplate sszNodeTemplate) {
//    this.sszNodeTemplate = sszNodeTemplate;
//    return this;
//  }
//
//  public TreeBuilder sszSuperNode(int superNodeDepth) {
//    this.superNodeDepth = superNodeDepth;
//    return this;
//  }

  public TreeBuilder addElements(Collection<? extends TreeNode> elements) {
    firstElements.addAll(elements);
    return this;
  }

  public TreeNode build() {
    validate();
    return null;
  }

  private void validate() {
    if (depth == 0) {
      throw new IllegalStateException("Tree depth was not specified");
    }
  }
}
