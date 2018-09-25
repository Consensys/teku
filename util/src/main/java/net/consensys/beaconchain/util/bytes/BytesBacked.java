package net.consensys.beaconchain.util.bytes;

/**
 * Base interface for a value whose content is stored as bytes.
 */
public interface BytesBacked {
  /**
   * @return The underlying backing bytes of the value.
   */
  BytesValue bytes();
}
