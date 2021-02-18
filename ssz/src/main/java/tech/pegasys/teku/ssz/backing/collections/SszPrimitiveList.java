package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszPrimitiveList<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszPrimitiveCollection<ElementT, SszElementT>, SszList<SszElementT>
{

  @Override
  SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy();
}
