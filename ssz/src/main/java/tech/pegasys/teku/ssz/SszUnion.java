package tech.pegasys.teku.ssz;

import tech.pegasys.teku.ssz.schema.SszUnionSchema;

public interface SszUnion extends SszData {

  int getSelector();

  SszData getValue();

  @Override
  SszUnionSchema<?> getSchema();

  @Override
  default boolean isWritableSupported() {
    return false;
  }

  @Override
  default SszMutableComposite<SszData> createWritableCopy() {
    throw new UnsupportedOperationException();
  }
}
