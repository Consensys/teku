package net.consensys.beaconchain.ethereum.core;

import static com.google.common.base.Preconditions.checkArgument;

import net.consensys.beaconchain.ethereum.rlp.RLPInput;
import net.consensys.beaconchain.ethereum.rlp.RLPOutput;
import net.consensys.beaconchain.util.bytes.BytesValue;
import net.consensys.beaconchain.util.bytes.DelegatingBytesValue;

public class LogTopic extends DelegatingBytesValue {

  public static final int SIZE = 32;

  private LogTopic(BytesValue bytes) {
    super(bytes);
    checkArgument(bytes.size() == SIZE, "A log topic must be be %s bytes long, got %s", SIZE,
        bytes.size());
  }

  public static LogTopic create(BytesValue bytes) {
    return new LogTopic(bytes);
  }

  public static LogTopic wrap(BytesValue bytes) {
    return new LogTopic(bytes);
  }

  public static LogTopic of(BytesValue bytes) {
    return new LogTopic(bytes.copy());
  }

  public static LogTopic fromHexString(String str) {
    return new LogTopic(BytesValue.fromHexString(str));
  }

  /**
   * Reads the log topic from the provided RLP input.
   *
   * @param in the input from which to decode the log topic.
   * @return the read log topic.
   */
  public static LogTopic readFrom(RLPInput in) {
    return new LogTopic(in.readBytesValue());
  }

  /**
   * Writes the log topic to the provided RLP output.
   *
   * @param out the output in which to encode the log topic.
   */
  public void writeTo(RLPOutput out) {
    out.writeBytesValue(this);
  }
}
