package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.SszVector;

public interface SszPrimitiveVector<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszPrimitiveCollection<ElementT, SszElementT>, SszVector<SszElementT>
{

  @Override
  SszMutablePrimitiveVector<ElementT, SszElementT> createWritableCopy();
}
