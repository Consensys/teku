package net.consensys.beaconchain.util.bytes;

public class DelegatingBytesValue extends BaseDelegatingBytesValue<BytesValue>
    implements BytesValue {

  protected DelegatingBytesValue(BytesValue wrapped) {
    super(unwrap(wrapped));
  }

  // Make sure we don't end-up with giant chains of delegating through wrapping.
  private static BytesValue unwrap(BytesValue v) {
    // Using a loop, because we could have DelegatingBytesValue intertwined with
    // DelegatingMutableBytesValue in theory.
    while (v instanceof BaseDelegatingBytesValue) {
      v = ((BaseDelegatingBytesValue) v).wrapped;
    }
    return v;
  }
}
