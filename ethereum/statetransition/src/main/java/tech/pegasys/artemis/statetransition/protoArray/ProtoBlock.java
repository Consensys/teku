package tech.pegasys.artemis.statetransition.protoArray;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

import java.util.Optional;

public class ProtoBlock {

  // child with greatest weight
  private Optional<Integer> bestChildIndex;

  // descendant with greatest weight
  private Integer bestDescendantIndex;

  // effective total balance of the validators that have voted
  // (using their latest attestation) for this block or one of its descendants
  private UnsignedLong weight;

  private final Bytes32 root;
  private final Bytes32 parentRoot;

  public ProtoBlock(
          Integer bestChildIndex,
          int bestDescendantIndex,
          UnsignedLong weight,
          Bytes32 root,
          Bytes32 parentRoot) {
    this.bestChildIndex = Optional.ofNullable(bestChildIndex);
    this.bestDescendantIndex = bestDescendantIndex;
    this.weight = weight;
    this.root = root;
    this.parentRoot = parentRoot;
  }

  public static ProtoBlock createNewProtoBlock(
          int blockIndex,
          Bytes32 root,
          Bytes32 parentRoot) {
    return new ProtoBlock(
            null,
            blockIndex,
            UnsignedLong.ZERO,
            root,
            parentRoot);
  }

  public Optional<Integer> getBestChildIndex() {
    return bestChildIndex;
  }

  public void setBestChildIndex(int bestChildIndex) {
    this.bestChildIndex = Optional.of(bestChildIndex);
  }

  public Integer getBestDescendantIndex() {
    return bestDescendantIndex;
  }

  public void setBestDescendantIndex(int bestDescendantIndex) {
    this.bestDescendantIndex = bestDescendantIndex;
  }

  public UnsignedLong getWeight() {
    return weight;
  }

  public void setWeight(UnsignedLong weight) {
    this.weight = weight;
  }

  public Bytes32 getRoot() {
    return root;
  }

  public Bytes32 getParentRoot() {
    return parentRoot;
  }
}
