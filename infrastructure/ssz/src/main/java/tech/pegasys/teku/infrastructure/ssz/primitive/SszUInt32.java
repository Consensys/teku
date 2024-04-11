package tech.pegasys.teku.infrastructure.ssz.primitive;

import tech.pegasys.teku.infrastructure.ssz.SszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SszUInt32 extends SszPrimitive<Integer, SszUInt32> {
}

interface SszUInt32Schema extends SszPrimitiveSchema<Integer, SszUInt32> {}


class Slot extends SszUInt64 {

  public Slot(UInt64 val) {
    super(val);
  }

  @Override
  public AbstractSszPrimitiveSchema<UInt64, SszUInt64> getSchema() {
    return super.getSchema();
  }
}

class SlotSchema extends SszPrimitiveSchemas.SszUInt64Schema {
  @Override
  public Slot boxed(UInt64 rawValue) {
    return new Slot(rawValue);
  }


}