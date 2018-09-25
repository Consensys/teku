package net.consensys.beaconchain.util.bytes;

/**
 * Base abstract implementation for {@link Bytes32Backed} implementations.
 */
public class AbstractBytes32Backed implements Bytes32Backed {
  protected final Bytes32 bytes;

  protected AbstractBytes32Backed(Bytes32 bytes) {
    this.bytes = bytes;
  }

  @Override
  public Bytes32 bytes() {
    return bytes;
  }
}
