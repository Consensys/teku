package net.consensys.beaconchain.ethereum.core;

import net.consensys.beaconchain.util.uint.UInt256;


/**
 * A block header capable of being processed.
 */
public class ProcessableBlockHeader {

  protected final Hash parentHash;

  protected final Address coinbase;

  protected final UInt256 difficulty;

  protected final long number;

  protected final long gasLimit;

  // The block creation timestamp (seconds since the unix epoch)
  protected final long timestamp;

  protected ProcessableBlockHeader(final Hash parentHash, final Address coinbase,
      final UInt256 difficulty, final long number, final long gasLimit, final long timestamp) {
    this.parentHash = parentHash;
    this.coinbase = coinbase;
    this.difficulty = difficulty;
    this.number = number;
    this.gasLimit = gasLimit;
    this.timestamp = timestamp;
  }

  /**
   * Returns the block parent block hash.
   *
   * @return the block parent block hash
   */
  public Hash parentHash() {
    return parentHash;
  }

  /**
   * Returns the block coinbase address.
   *
   * @return the block coinbase address
   */
  public Address coinbase() {
    return coinbase;
  }

  /**
   * Returns the block difficulty.
   *
   * @return the block difficulty
   */
  public UInt256 difficulty() {
    return difficulty;
  }

  /**
   * Returns the block number.
   *
   * @return the block number
   */
  public long number() {
    return number;
  }

  /**
   * Return the block gas limit.
   *
   * @return the block gas limit
   */
  public long gasLimit() {
    return gasLimit;
  }

  /**
   * Return the block timestamp.
   *
   * @return the block timestamp
   */
  public long timestamp() {
    return timestamp;
  }

}
