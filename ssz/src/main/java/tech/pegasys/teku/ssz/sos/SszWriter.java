package tech.pegasys.teku.ssz.sos;

import org.apache.tuweni.bytes.Bytes;

public interface SszWriter {

  default void write(Bytes bytes) {
    write(bytes.toArrayUnsafe());
  }

  default void write(byte[] bytes) {
    write(bytes, 0, bytes.length);
  }

  void write(byte[] bytes, int offset, int length);
}
