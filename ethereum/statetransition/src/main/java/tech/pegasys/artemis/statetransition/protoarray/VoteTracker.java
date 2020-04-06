package tech.pegasys.artemis.statetransition.protoarray;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class VoteTracker {

  private final Bytes32 currentRoot;

  private Bytes32 nextRoot;
  private UnsignedLong nextEpoch;

  public static VoteTracker DEFAULT = new VoteTracker(
          Bytes32.ZERO, Bytes32.ZERO, UnsignedLong.ZERO);

  public VoteTracker(Bytes32 currentRoot,
                     Bytes32 nextRoot,
                     UnsignedLong nextEpoch) {
    this.currentRoot = currentRoot;
    this.nextRoot = nextRoot;
    this.nextEpoch = nextEpoch;
  }

  public void setNextRoot(Bytes32 nextRoot) {
    this.nextRoot = nextRoot;
  }

  public void setNextEpoch(UnsignedLong nextEpoch) {
    this.nextEpoch = nextEpoch;
  }

  public Bytes32 getCurrentRoot() {
    return currentRoot;
  }

  public Bytes32 getNextRoot() {
    return nextRoot;
  }

  public UnsignedLong getNextEpoch() {
    return nextEpoch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof VoteTracker)) return false;
    VoteTracker that = (VoteTracker) o;
    return Objects.equal(getCurrentRoot(), that.getCurrentRoot()) &&
            Objects.equal(getNextRoot(), that.getNextRoot()) &&
            Objects.equal(getNextEpoch(), that.getNextEpoch());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getCurrentRoot(), getNextRoot(), getNextEpoch());
  }
}
