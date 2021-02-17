/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.ssz.backing;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.BranchNode;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SszTestUtils {

  public static List<Integer> getVectorLengths(AbstractSszContainerSchema<?> sszContainerSchema) {
    return sszContainerSchema.getFieldSchemas().stream()
        .filter(t -> t instanceof SszVectorSchema)
        .map(t -> (SszVectorSchema<?, ?>) t)
        .map(SszVectorSchema::getLength)
        .collect(Collectors.toList());
  }

  /** Compares two views by their getters recursively (if views are composite) */
  public static boolean equalsByGetters(SszData v1, SszData v2) {
    if (!v1.getSchema().equals(v2.getSchema())) {
      return false;
    }
    if (v1 instanceof SszComposite) {
      SszComposite<?> c1 = (SszComposite<?>) v1;
      SszComposite<?> c2 = (SszComposite<?>) v2;
      if (c1.size() != c2.size()) {
        return false;
      }
      for (int i = 0; i < c1.size(); i++) {
        if (!equalsByGetters((SszData) c1.get(i), (SszData) c2.get(i))) {
          return false;
        }
      }
      return true;
    } else {
      return v1.equals(v2);
    }
  }

  public static SszBitlist not(SszBitlist bitlist) {
    List<Boolean> notList = bitlist.stream().map(b -> !b.get()).collect(Collectors.toList());
    int[] notBitIndexes = IntStream.range(0, notList.size()).filter(notList::get).toArray();
    return bitlist.getSchema().ofBits(bitlist.size(), notBitIndexes);
  }

  /** Estimates the number of 'non-default' tree nodes */
  public static int estimateNonDefaultNodes(TreeNode node) {
    if (node instanceof LeafNode) {
      return 1;
    } else {
      BranchNode branchNode = (BranchNode) node;
      if (branchNode.left() == branchNode.right()) {
        return 0;
      } else {
        return estimateNonDefaultNodes(branchNode.left())
            + estimateNonDefaultNodes(branchNode.right())
            + 1;
      }
    }
  }

  /** Dumps the tree to stdout */
  public static String dumpBinaryTree(TreeNode node) {
    StringBuilder ret = new StringBuilder();
    dumpBinaryTreeRec(node, "", false, s -> ret.append(s).append('\n'));
    return ret.toString();
  }

  private static void dumpBinaryTreeRec(
      TreeNode node, String prefix, boolean printCommit, Consumer<String> linesConsumer) {
    if (node instanceof LeafNode) {
      LeafNode leafNode = (LeafNode) node;
      linesConsumer.accept(prefix + leafNode);
    } else {
      BranchNode branchNode = (BranchNode) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + branchNode;
      }
      if (branchNode.left() instanceof LeafNode) {
        linesConsumer.accept(prefix + "├─" + branchNode.left());
      } else {
        linesConsumer.accept(prefix + s);
        dumpBinaryTreeRec(branchNode.left(), prefix + "│ ", printCommit, linesConsumer);
      }
      if (branchNode.right() instanceof LeafNode) {
        linesConsumer.accept(prefix + "└─" + branchNode.right());
      } else {
        linesConsumer.accept(prefix + "└─┐");
        dumpBinaryTreeRec(branchNode.right(), prefix + "  ", printCommit, linesConsumer);
      }
    }
  }
}
