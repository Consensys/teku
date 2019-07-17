package tech.pegasys.artemis.storage;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class LatestMessage {

  private UnsignedLong epoch;
  private Bytes32 root;

  public LatestMessage(UnsignedLong epoch, Bytes32 root) {
    this.epoch = epoch;
    this.root = root;
  }
  public UnsignedLong getEpoch() {
    return epoch;
  }

  public void setEpoch(UnsignedLong epoch) {
    this.epoch = epoch;
  }

  public Bytes32 getRoot() {
    return root;
  }

  public void setRoot(Bytes32 root) {
    this.root = root;
  }
}
