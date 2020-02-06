package tech.pegasys.artemis.util.backing.tree;

import tech.pegasys.artemis.util.backing.tree.TreeNode.Commit;
import tech.pegasys.artemis.util.backing.tree.TreeNode.Root;

public class TreeUtil {

  public static int estimateNonDefaultNodes(TreeNode node) {
    if (node instanceof Root) {
      return 1;
    } else {
      Commit commitNode = (Commit) node;
      if (commitNode.left() == commitNode.right()) {
        return 0;
      } else {
        return estimateNonDefaultNodes(commitNode.left()) + estimateNonDefaultNodes(
            commitNode.right()) + 1;
      }
    }
  }

  public static void dumpBinaryTree(TreeNode node) {
    dumpBinaryTreeRec(node, "", false);
  }

  private static void dumpBinaryTreeRec(TreeNode node, String prefix, boolean printCommit) {
    if (node instanceof Root) {
      Root rootNode = (Root) node;
      System.out.println(prefix + rootNode);
    } else {
      Commit commitNode = (Commit) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + commitNode;
      }
      if (commitNode.left() instanceof Root) {
        System.out.println(prefix + "├─" + commitNode.left());
      } else {
        System.out.println(prefix + s);
        dumpBinaryTreeRec(commitNode.left(), prefix + "│ ", printCommit);
      }
      if (commitNode.right() instanceof Root) {
        System.out.println(prefix + "└─" + commitNode.right());
      } else {
        System.out.println(prefix + "└─┐");
        dumpBinaryTreeRec(commitNode.right(), prefix + "  ", printCommit);
      }
    }
  }

}
