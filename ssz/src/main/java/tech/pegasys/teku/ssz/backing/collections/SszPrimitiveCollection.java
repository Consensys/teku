package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszCollection;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszPrimitiveCollection<ElementT, SszElementT extends SszPrimitive<ElementT>> extends
    SszCollection<SszElementT> {

  default ElementT getElement(int index) {
    return get(index).get();
  }
}
