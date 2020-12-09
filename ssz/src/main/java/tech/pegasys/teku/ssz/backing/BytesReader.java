package tech.pegasys.teku.ssz.backing;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.type.SSZType;

public interface BytesReader {

  static BytesReader fromBytes(Bytes bytes) {
    return new SimpleBytesReader(bytes);
  }

  int getAvailableBytes();

  BytesReader slice(int size);

  Bytes read(int length);
}

class SimpleBytesReader implements BytesReader {
  Bytes bytes;
  int offset = 0;

  public SimpleBytesReader(Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public int getAvailableBytes() {
    return bytes.size() - offset;
  }

  @Override
  public BytesReader slice(int size) {
    checkOffset(offset + size);
    SimpleBytesReader ret = new SimpleBytesReader(bytes.slice(offset, size));
    offset += size;
    return ret;
  }

  @Override
  public Bytes read(int length) {
    checkOffset(offset + length);
    Bytes ret = bytes.slice(offset, length);
    offset += length;
    return ret;
  }

  private void checkOffset(int offset) {
    if (offset > bytes.size()) {
      throw new IndexOutOfBoundsException();
    }
  }

}