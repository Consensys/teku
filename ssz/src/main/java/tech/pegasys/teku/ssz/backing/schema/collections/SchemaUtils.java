package tech.pegasys.teku.ssz.backing.schema.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

public class SchemaUtils {

  public static TreeNode createTreeFromBytes(Bytes bytes, int treeDepth) {
    return TreeUtil.createTree(split(bytes, LeafNode.MAX_BYTE_SIZE).stream().map(LeafNode::create).collect(
        Collectors.toList()), treeDepth);
  }

  public static List<Bytes> split(Bytes bytes, int chunkSize) {
    List<Bytes> ret = new ArrayList<>();
    int off = 0;
    int size = bytes.size();
    while (off < size) {
      Bytes leafData = bytes.slice(off, Integer.min(chunkSize, size - off));
      ret.add(leafData);
      off += chunkSize;
    }
    return ret;
  }
}
