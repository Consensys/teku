package tech.pegasys.artemis.protoarray;

import org.apache.tuweni.bytes.Bytes32;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes32;

public class HashUtil {

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(int i) {
    return int_to_bytes32(Integer.toUnsignedLong(i + 1));
  }
}
