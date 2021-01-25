package tech.pegasys.teku.ssz.sos;

import org.apache.tuweni.bytes.Bytes;

public class SszByteArrayWriter implements SszWriter {
  private final byte[] bytes;
  private int size = 0;

  public SszByteArrayWriter(int maxSize) {
    bytes = new byte[maxSize];
  }

  @Override
  public void write(byte[] bytes, int offset, int length) {
    System.arraycopy(bytes, offset, this.bytes, this.size, length);
    this.size += length;
  }

  public byte[] getBytesArray() {
    return bytes;
  }

  public int getLength() {
    return size;
  }

  public Bytes toBytes() {
    return Bytes.wrap(bytes, 0, size);
  }
}
