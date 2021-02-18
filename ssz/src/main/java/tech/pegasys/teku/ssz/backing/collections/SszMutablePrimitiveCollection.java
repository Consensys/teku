package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszMutableComposite;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchema;

public interface SszMutablePrimitiveCollection<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszPrimitiveCollection<ElementT, SszElementT>, SszMutableComposite<SszElementT> {

  default SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  default void setElement(int index, ElementT primitiveValue) {
    SszElementT sszData = getPrimitiveElementSchema().toSszData(primitiveValue);
    set(index, sszData);
  }

  @Override
  SszPrimitiveCollection<ElementT, SszElementT> commitChanges();
}
