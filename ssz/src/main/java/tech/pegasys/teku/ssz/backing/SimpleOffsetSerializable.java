package tech.pegasys.teku.ssz.backing;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.sos.SszWriter;

public interface SimpleOffsetSerializable {

  Bytes sszSerialize();

  int sszSerialize(SszWriter writer);
}
