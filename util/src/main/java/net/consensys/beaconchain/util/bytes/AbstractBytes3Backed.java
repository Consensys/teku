package net.consensys.beaconchain.util.bytes;

/**
 * Base abstract implementation for {@link Bytes3Backed} implementations.
 */
public class AbstractBytes3Backed implements Bytes3Backed {
  protected final Bytes3 bytes;

  protected AbstractBytes3Backed(Bytes3 bytes) {
    this.bytes = bytes;
  }

  @Override
  public Bytes3 bytes() {
    return bytes;
  }
}
