package net.consensys.beaconchain.util.bytes;

import java.security.MessageDigest;

abstract class BaseDelegatingBytesValue<T extends BytesValue> implements BytesValue {

  protected final T wrapped;

  BaseDelegatingBytesValue(T wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public int size() {
    return wrapped.size();
  }

  @Override
  public byte get(int i) {
    return wrapped.get(i);
  }

  @Override
  public int getInt(int i) {
    return wrapped.getInt(i);
  }

  @Override
  public long getLong(int i) {
    return wrapped.getLong(i);
  }

  @Override
  public BytesValue slice(int index) {
    return wrapped.slice(index);
  }

  @Override
  public BytesValue slice(int index, int length) {
    return wrapped.slice(index, length);
  }

  @Override
  public BytesValue copy() {
    return wrapped.copy();
  }

  @Override
  public MutableBytesValue mutableCopy() {
    return wrapped.mutableCopy();
  }

  @Override
  public void copyTo(MutableBytesValue destination) {
    wrapped.copyTo(destination);
  }

  @Override
  public void copyTo(MutableBytesValue destination, int destinationOffset) {
    wrapped.copyTo(destination, destinationOffset);
  }

  @Override
  public int commonPrefixLength(BytesValue other) {
    return wrapped.commonPrefixLength(other);
  }

  @Override
  public BytesValue commonPrefix(BytesValue other) {
    return wrapped.commonPrefix(other);
  }

  @Override
  public void update(MessageDigest digest) {
    wrapped.update(digest);
  }

  @Override
  public boolean isZero() {
    return wrapped.isZero();
  }

  @Override
  public boolean equals(Object other) {
    return wrapped.equals(other);
  }

  @Override
  public int hashCode() {
    return wrapped.hashCode();
  }

  @Override
  public String toString() {
    return wrapped.toString();
  }
}
