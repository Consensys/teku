package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszList;

public interface SszListSchemaIfc<ElementDataT extends SszData, SszListT extends SszList<ElementDataT>>
      extends SszCollectionSchemaIfc<ElementDataT, SszListT> {

    static <ElementDataT extends SszData> SszListSchemaIfc<ElementDataT, ?> create(
        SszSchema<ElementDataT> elementSchema, long maxLength) {
      return new SszListSchema<>(elementSchema, maxLength);
    }

    static <ElementDataT extends SszData> SszListSchemaIfc<ElementDataT, ?> create(
        SszSchema<ElementDataT> elementSchema, long maxLength, SszSchemaHints hints) {
      return new SszListSchema<>(elementSchema, maxLength, hints);
    }
}
