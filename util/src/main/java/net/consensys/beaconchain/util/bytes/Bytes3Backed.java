package net.consensys.beaconchain.util.bytes;

/**
 * Base interface for a value whose content is stored with exactly 3 bytes.
 */
public interface Bytes3Backed extends BytesBacked {
  @Override
  Bytes3 bytes();
}
