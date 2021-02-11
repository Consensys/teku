package tech.pegasys.teku.ssz.backing.schema.collections;

import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;
import tech.pegasys.teku.ssz.backing.collections.SszBitlistImpl;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public class SszBitlistSchemaImpl extends AbstractSszListSchema<SszBit, SszBitlist> implements
    SszBitlistSchema<SszBitlist> {

  public SszBitlistSchemaImpl(long maxLength) {
    super(SszPrimitiveSchemas.BIT_SCHEMA, maxLength);
  }

  @Override
  public SszBitlist createFromBackingNode(TreeNode node) {
    return new SszBitlistImpl(this, node);
  }

  @Override
  public SszBitlist fromLegacy(Bitlist bitlist) {
    return new SszBitlistImpl(this, bitlist);
  }

  @Override
  public SszBitlist createZero(int zeroBitsCount) {
    return fromLegacy(new Bitlist(zeroBitsCount, getMaxLength()));
  }
}
