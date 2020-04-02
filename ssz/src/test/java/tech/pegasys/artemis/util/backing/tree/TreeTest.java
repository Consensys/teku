package tech.pegasys.artemis.util.backing.tree;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode.BranchNode;
import tech.pegasys.artemis.util.backing.tree.TreeUpdates.Update;

public class TreeTest {

  public static TreeNode newTestLeaf(long l) {
    return TreeNode.createLeafNode(Bytes32.leftPad(Bytes.ofUnsignedLong(l, ByteOrder.BIG_ENDIAN)));
  }

  @Test
  public void testCreateTreeFromLeafNodes() {
    BranchNode n1 =
        (BranchNode)
            TreeUtil.createTree(
                IntStream.range(0, 5).mapToObj(TreeTest::newTestLeaf).collect(Collectors.toList()));

    BranchNode n10 = (BranchNode) n1.left();
    BranchNode n11 = (BranchNode) n1.right();
    BranchNode n100 = (BranchNode) n10.left();
    BranchNode n101 = (BranchNode) n10.right();
    BranchNode n110 = (BranchNode) n11.left();
    BranchNode n111 = (BranchNode) n11.right();

    assertThat(n100.left()).isEqualTo(newTestLeaf(0));
    assertThat(n100.right()).isEqualTo(newTestLeaf(1));
    assertThat(n101.left()).isEqualTo(newTestLeaf(2));
    assertThat(n101.right()).isEqualTo(newTestLeaf(3));
    assertThat(n110.left()).isEqualTo(newTestLeaf(4));
    assertThat(n110.right()).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n111.left()).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n111.right()).isSameAs(TreeUtil.ZERO_LEAF);

    assertThat(n1.get(0b1)).isSameAs(n1);
    assertThat(n1.get(0b10)).isSameAs(n10);
    assertThat(n1.get(0b111)).isSameAs(n111);
    assertThat(n1.get(0b1000)).isSameAs(n100.left());
    assertThat(n1.get(0b1100)).isSameAs(n110.left());
    assertThat(n10.get(0b100)).isSameAs(n100.left());
    assertThat(n11.get(0b100)).isSameAs(n110.left());
  }

  @Test
  public void testZeroLeafDefaultTree() {
    TreeNode n1 = TreeUtil.createDefaultTree(5, TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b1000)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b1111)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b101));
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b110));
    assertThat(n1.get(0b100)).isSameAs(n1.get(0b111));
    assertThat(n1.get(0b10)).isSameAs(n1.get(0b11));
  }

  @Test
  public void testNonZeroLeafDefaultTree() {
    TreeNode zeroTree = TreeUtil.createDefaultTree(5, TreeUtil.ZERO_LEAF);

    TreeNode defaultLeaf = newTestLeaf(111);
    BranchNode n1 = (BranchNode) TreeUtil.createDefaultTree(5, defaultLeaf);
    assertThat(n1.get(0b1000)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1001)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1100)).isSameAs(defaultLeaf);
    assertThat(n1.get(0b1101)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b1111)).isSameAs(TreeUtil.ZERO_LEAF);
    assertThat(n1.get(0b111)).isSameAs(zeroTree.get(0b111));
  }

  @Test
  public void testUpdated() {
    TreeNode zeroTree = TreeUtil.createDefaultTree(8, TreeUtil.ZERO_LEAF);
    TreeNode t1 = zeroTree.updated(8 + 0, newTestLeaf(111));
    TreeNode t1_ = zeroTree.updated(8 + 0, newTestLeaf(111));
    assertThat(t1).isNotSameAs(t1_);
    assertThat(t1.get(8 + 0)).isEqualTo(newTestLeaf(111));
    assertThat(IntStream.range(1, 8).mapToObj(idx -> t1.get(8 + idx)))
        .containsOnly(TreeUtil.ZERO_LEAF);
    assertThat(t1.hashTreeRoot()).isEqualTo(t1_.hashTreeRoot());

    TreeNode t2 = t1.updated(8 + 3, newTestLeaf(222));
    TreeNode t2_ =
        zeroTree.updated(
            new TreeUpdates(
                List.of(new Update(8 + 0, newTestLeaf(111)), new Update(8 + 3, newTestLeaf(222)))));
    assertThat(t2).isNotSameAs(t2_);
    assertThat(t2.get(8 + 0)).isEqualTo(newTestLeaf(111));
    assertThat(t2.get(8 + 3)).isEqualTo(newTestLeaf(222));
    assertThat(IntStream.of(1, 2, 4, 5, 6, 7).mapToObj(idx -> t2.get(8 + idx)))
        .containsOnly(TreeUtil.ZERO_LEAF);
    assertThat(t2.hashTreeRoot()).isEqualTo(t2_.hashTreeRoot());

    TreeNode zeroTree_ =
        t2.updated(
            new TreeUpdates(
                List.of(
                    new Update(8 + 0, TreeUtil.ZERO_LEAF), new Update(8 + 3, TreeUtil.ZERO_LEAF))));
    assertThat(zeroTree.hashTreeRoot()).isEqualTo(zeroTree_.hashTreeRoot());
  }

  @Test
  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  public void testHashThreadSafe() {
    TreeNode tree = TreeUtil.createDefaultTree(32 * 1024, newTestLeaf(111));
    ExecutorService threadPool = Executors.newFixedThreadPool(512);
    CountDownLatch latch = new CountDownLatch(1);
    List<Future<Bytes32>> hasheFuts =
        IntStream.range(0, 512)
            .mapToObj(
                i ->
                    threadPool.submit(
                        () -> {
                          latch.await();
                          return tree.hashTreeRoot();
                        }))
            .collect(Collectors.toList());
    latch.countDown();
    Stream<Bytes32> hashes =
        hasheFuts.stream()
            .map(
                f -> {
                  try {
                    return f.get();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
    assertThat(hashes).containsOnly(tree.hashTreeRoot());
  }
}
