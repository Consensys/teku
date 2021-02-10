package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszVector;

public interface SszVectorSchema<ElementDataT extends SszData, SszVectorT extends SszVector<ElementDataT>>
    extends SszCollectionSchema<ElementDataT, SszVectorT> {

  default int getLength() {
    long maxLength = getMaxLength();
    if (maxLength > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Vector size too large: " + maxLength);
    }
    return (int) maxLength;
  }


  static <ElementDataT extends SszData> SszVectorSchema<ElementDataT, ?> create(
      SszSchema<ElementDataT> elementSchema, long length) {
    return new SszVectorSchemaImpl<>(elementSchema, length);
  }

  static <ElementDataT extends SszData> SszVectorSchema<ElementDataT, ?> create(
      SszSchema<ElementDataT> elementSchema, long length, SszSchemaHints hints) {
    return new SszVectorSchemaImpl<>(elementSchema, length, false, hints);
  }
}
