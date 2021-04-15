package tech.pegasys.teku.ssz.schema.collections;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.schema.collections.impl.SszByteListSchemaImpl;

public interface SszByteListSchema<SszListT extends SszByteList>
    extends SszPrimitiveListSchema<Byte, SszByte, SszListT> {

  SszListT fromBytes(Bytes bytes);

  static SszByteListSchema<SszByteList> create(long maxLength) {
    return new SszByteListSchemaImpl<>(maxLength);
  }
}
