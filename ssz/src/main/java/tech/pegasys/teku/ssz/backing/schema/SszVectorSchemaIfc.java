package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszVector;

public interface SszVectorSchemaIfc<ElementDataT extends SszData, SszVectorT extends SszVector<ElementDataT>>
    extends SszCollectionSchemaIfc<ElementDataT, SszVectorT> {

  static <ElementDataT extends SszData> SszVectorSchemaIfc<ElementDataT, ?> create(
      SszSchema<ElementDataT> elementSchema, long length) {
    return new SszVectorSchema<>(elementSchema, length);
  }

  static <ElementDataT extends SszData> SszVectorSchemaIfc<ElementDataT, ?> create(
      SszSchema<ElementDataT> elementSchema, long length, SszSchemaHints hints) {
    return new SszVectorSchema<>(elementSchema, length, false, hints);
  }
}
