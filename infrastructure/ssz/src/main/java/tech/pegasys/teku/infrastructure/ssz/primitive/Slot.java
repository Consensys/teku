package tech.pegasys.teku.infrastructure.ssz.primitive;

import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszUInt64Schema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Slot extends SszUInt64 {

  public static class SlotSchema extends AbstractSszUInt64Schema<Slot> {

    @Override
    public Slot boxed(UInt64 rawValue) {
      return new Slot(rawValue);
    }
  }
  public Slot(UInt64 val) {
    super(val);
  }

  @Override
  public SlotSchema getSchema() {
    return (SlotSchema) super.getSchema();
  }
}
