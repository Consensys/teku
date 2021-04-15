package tech.pegasys.teku.ssz.collections;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;

public interface SszByteList extends SszPrimitiveList<Byte, SszByte> {

  static SszByteList fromBytes(Bytes byteVector) {
    return SszByteListSchema.create(byteVector.size()).fromBytes(byteVector);
  }

  default Bytes getBytes() {
    byte[] data = new byte[size()];
    for (int i = 0; i < size(); i++) {
      data[i] = getElement(i);
    }
    return Bytes.wrap(data);
  }

  @Override
  default SszMutablePrimitiveList<Byte, SszByte> createWritableCopy() {
    throw new UnsupportedOperationException("Not supported here");
  }
}
