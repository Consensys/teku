package net.consensys.beaconchain.util.uint;

import net.consensys.beaconchain.util.bytes.Bytes32;

import java.math.BigInteger;

/**
 * An unsigned 256-bits precision number.
 *
 * This class is essentially a "raw" {@link UInt256Value}, a 256-bits precision unsigned number of
 * no particular unit.
 */
public interface UInt256 extends UInt256Value<UInt256> {
  /** The value 0. */
  UInt256 ZERO = of(0);
  /** The value 1. */
  UInt256 ONE = of(1);
  /** The value 32. */
  UInt256 U_32 = of(32);

  static UInt256 of(long value) {
    return new DefaultUInt256(UInt256Bytes.of(value));
  }

  static UInt256 of(BigInteger value) {
    return new DefaultUInt256(UInt256Bytes.of(value));
  }

  static UInt256 wrap(Bytes32 value) {
    return new DefaultUInt256(value);
  }

  static Counter<UInt256> newCounter() {
    return DefaultUInt256.newVar();
  }

  static Counter<UInt256> newCounter(UInt256Value<?> initialValue) {
    Counter<UInt256> c = DefaultUInt256.newVar();
    initialValue.bytes().copyTo(c.bytes());
    return c;
  }

  static UInt256 fromHexString(String str) {
    return new DefaultUInt256(Bytes32.fromHexStringLenient(str));
  }
}
