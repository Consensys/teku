package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszData;

public interface SszCollectionSchemaIfc<
    SszElementT extends SszData, SszCollectionT extends SszCollection<SszElementT>>
    extends SszCompositeSchema<SszCollectionT> {

  SszSchema<SszElementT> getElementSchema();
}
