package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.schema.SszCollectionSchema;

public interface SszPrimitiveCollection<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszCollection<SszElementT> {

  default ElementT getElement(int index) {
    return get(index).get();
  }

  @Override
  SszCollectionSchema<SszElementT, ?> getSchema();

  @Override
  SszMutablePrimitiveCollection<ElementT, SszElementT> createWritableCopy();
}
