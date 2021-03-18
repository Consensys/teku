package tech.pegasys.teku.ssz.collections.impl;

import tech.pegasys.teku.ssz.SszComposite;
import tech.pegasys.teku.ssz.SszMutableComposite;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.collections.SszMutablePrimitiveCollection;
import tech.pegasys.teku.ssz.collections.SszPrimitiveCollection;
import tech.pegasys.teku.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.ssz.impl.AbstractSszMutableCollection;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchema;

public abstract class AbstractSszMutablePrimitiveCollection<
    ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
  extends AbstractSszMutableCollection<SszElementT, SszElementT>
  implements SszMutablePrimitiveCollection<ElementT, SszElementT> {

  private final SszPrimitiveSchema<ElementT, SszElementT> elementSchemaCache;

  public AbstractSszMutablePrimitiveCollection(
      AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
    elementSchemaCache = (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  @Override
  public SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return elementSchemaCache;
  }

  @Override
  protected void validateChildSchema(int index, SszElementT value) {
    // no need to check primitive value schema
  }

  @Override
  public SszPrimitiveCollection<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveCollection<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutablePrimitiveCollection<
      ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException();
  }
}
