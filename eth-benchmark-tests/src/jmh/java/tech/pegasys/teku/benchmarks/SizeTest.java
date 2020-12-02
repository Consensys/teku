package tech.pegasys.teku.benchmarks;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class SizeTest {

  public TreeNode stateTree;

  @Test
  void a() throws InterruptedException {
    stateTree = createStateTree();
    Thread.sleep(100000000000L);
  }

  TreeNode createStateTree() {
    BLSPublicKey pubKey = BLSPublicKey.random(1);
    DataStructureUtil dataStructureUtil = new DataStructureUtil()
        .withPubKeyGenerator(() -> pubKey);
    System.out.println("Generating state...");
    BeaconState state = dataStructureUtil.randomBeaconState(100_000);
    System.out.println("Counting nodes...");
//    AtomicInteger branchCount = new AtomicInteger();
//    AtomicInteger leafCount = new AtomicInteger();
//    Map<Class<?>, AtomicInteger> counts = new HashMap<>();
//    state.getBackingNode().iterateAll(n ->
//        counts.computeIfAbsent(n.getClass(), __ -> new AtomicInteger()).incrementAndGet());
//    counts.entrySet().forEach(e -> {
//      System.out.println(e.getKey().getSimpleName() + ": " + e.getValue());
//    });
    Bytes serializeBytes = SimpleOffsetSerializer.serialize(state);
    System.out.println("Serialized size: " + serializeBytes.size());



    return state.getBackingNode();
  }
}
