package tech.pegasys.teku.ssz.backing;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.type.SSZType;

public interface BytesReader {

  int getAvailableBytes();

  BytesReader slice(int size);

  Bytes read(int length);
}
