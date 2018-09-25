package net.consensys.beaconchain.ethereum.core;

import static net.consensys.beaconchain.crypto.Hash.keccak256;

import net.consensys.beaconchain.ethereum.rlp.RLP;
import net.consensys.beaconchain.util.bytes.Bytes32;
import net.consensys.beaconchain.util.bytes.BytesValue;
import net.consensys.beaconchain.util.bytes.DelegatingBytes32;

import com.fasterxml.jackson.annotation.JsonCreator;

/** A 32-bytes hash value as used in Ethereum blocks, that is the result of the KEC algorithm. */
public class Hash extends DelegatingBytes32 {

  public static final Hash ZERO = new Hash(Bytes32.ZERO);

  public static final Hash EMPTY_TRIE_HASH = Hash.hash(RLP.NULL);

  public static final Hash EMPTY_LIST_HASH = Hash.hash(RLP.EMPTY_LIST);

  public static final Hash EMPTY = hash(BytesValue.EMPTY);

  private Hash(Bytes32 bytes) {
    super(bytes);
  }

  public static Hash hash(BytesValue value) {
    return new Hash(keccak256(value));
  }

  public static Hash wrap(Bytes32 bytes) {
    return new Hash(bytes);
  }

  /**
   * Parse an hexadecimal string representing a hash value.
   *
   * @param str An hexadecimal string (with or without the leading '0x') representing a valid hash
   *        value.
   * @return The parsed hash.
   * @throws NullPointerException if the provided string is {@code null}.
   * @throws IllegalArgumentException if the string is either not hexadecimal, or not the valid
   *         representation of a hash (not 32 bytes).
   */
  @JsonCreator
  public static Hash fromHexString(String str) {
    return new Hash(Bytes32.fromHexStringStrict(str));
  }

  public static Hash fromHexStringLenient(String str) {
    return new Hash(Bytes32.fromHexStringLenient(str));
  }
}
