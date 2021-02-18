package tech.pegasys.teku.ssz.backing.schema;

import tech.pegasys.teku.ssz.backing.SszData;

public interface SszPrimitiveSchema<RawType, SszType extends SszData> extends SszSchema<SszType> {

  SszType toSszData(RawType rawValue);
}
