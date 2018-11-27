package net.consensys.beaconchain.util.bytes;

/**
 * An implementation of {@link MutableBytes3} backed by a byte array ({@code byte[]}).
 */
class MutableArrayWrappingBytes3 extends MutableArrayWrappingBytesValue implements MutableBytes3 {

  MutableArrayWrappingBytes3(byte[] bytes) {
    this(bytes, 0);
  }

  MutableArrayWrappingBytes3(byte[] bytes, int offset) {
    super(bytes, offset, SIZE);
  }

  @Override
  public Bytes3 copy() {
    // We *must* override this method because ArrayWrappingBytes3 assumes that it is the case.
    return new ArrayWrappingBytes3(arrayCopy());
  }

  @Override
  public MutableBytes3 mutableCopy() {
    return new MutableArrayWrappingBytes3(arrayCopy());
  }
}
