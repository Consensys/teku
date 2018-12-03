package net.consensys.beaconchain.state;

import net.consensys.beaconchain.ethereum.core.Hash;
import net.consensys.beaconchain.util.bytes.Bytes3;

import com.google.common.annotations.VisibleForTesting;

import static com.google.common.base.Preconditions.checkArgument;


public class BeaconState {

  static class BeaconStateHelperFunctions {


    /**
     * Converts int to Bytes3.
     *
     * @param seed  converted
     * @return      converted Bytes3
     * @throws IllegalArgumentException if seed is a negative value.
     */
    @VisibleForTesting
    static Bytes3 intToBytes3(int seed) {
      checkArgument(seed > 0, "Expected positive seed but got %s", seed);
      byte[] bytes = new byte[3];
      bytes[0] = (byte) (seed >> 16);
      bytes[1] = (byte) (seed >> 8);
      bytes[2] = (byte) seed;
      return Bytes3.wrap(bytes);
    }

    /**
     * Converts byte[] to int.
     *
     * @param src   byte[]
     * @param pos   Index in Byte[] array
     * @return      converted int
     * @throws IllegalArgumentException if pos is a negative value.
     */
    @VisibleForTesting
    static int bytes3ToInt(Hash src, int pos) {
      checkArgument(pos > 0, "Expected positive pos but got %s", pos);
      return ((src.extractArray()[pos] & 0xF) << 16) |
          ((src.extractArray()[pos + 1] & 0xFF) << 8) |
          (src.extractArray()[pos + 2] & 0xFF);
    }
  }

}
