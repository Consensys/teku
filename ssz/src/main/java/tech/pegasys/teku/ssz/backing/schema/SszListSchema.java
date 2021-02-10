package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;

public interface SszListSchema<ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
      extends SszCollectionSchema<ElementDataT, SszListT> {

    static <ElementDataT extends SszData> SszListSchema<ElementDataT, SszList<ElementDataT>> create(
        SszSchema<ElementDataT> elementSchema, long maxLength) {
      return new SszListSchemaImpl<>(elementSchema, maxLength);
    }

    static <ElementDataT extends SszData> SszListSchema<ElementDataT, SszList<ElementDataT>> create(
        SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
      return new SszListSchemaImpl<>(elementSchema, maxLength, hints);
    }
}
