package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszMutableVector;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszMutablePrimitiveVector<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszMutablePrimitiveCollection<ElementT, SszElementT>, SszMutableVector<SszElementT> {

  @Override
  SszPrimitiveVector<ElementT, SszElementT> commitChanges();

  @Override
  SszMutablePrimitiveVector<ElementT, SszElementT> createWritableCopy();
}
