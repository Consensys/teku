package tech.pegasys.teku.ssz.backing.schema.collections;

import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;
import tech.pegasys.teku.ssz.backing.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public interface SszBitlistSchema<SszBitlistT extends SszBitlist> extends
    SszListSchema<SszBit, SszBitlistT> {

  static SszBitlistSchema<SszBitlist> create(long maxLength) {
    return new SszBitlistSchemaImpl(maxLength);
  }

  @Deprecated
  SszBitlistT fromLegacy(Bitlist bitlist);

  SszBitlistT createZero(int zeroBitsCount);
}
