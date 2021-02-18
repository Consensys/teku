package tech.pegasys.teku.ssz.backing.collections;

import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszMutableList;
import tech.pegasys.teku.ssz.backing.SszPrimitive;

public interface SszMutablePrimitiveList<ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
    extends SszMutablePrimitiveCollection<ElementT, SszElementT>, SszMutableList<SszElementT> {

  @Override
  SszPrimitiveList<ElementT, SszElementT> commitChanges();

  @Override
  SszMutablePrimitiveList<ElementT, SszElementT> createWritableCopy();
}
