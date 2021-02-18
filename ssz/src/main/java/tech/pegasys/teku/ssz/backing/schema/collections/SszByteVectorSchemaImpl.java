package tech.pegasys.teku.ssz.backing.schema.collections;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.collections.SszBitvector;
import tech.pegasys.teku.ssz.backing.collections.SszByteVector;
import tech.pegasys.teku.ssz.backing.collections.SszByteVectorImpl;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszVectorSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;

public class SszByteVectorSchemaImpl<SszVectorT extends SszByteVector>
    extends AbstractSszVectorSchema<SszByte, SszVectorT>
    implements SszByteVectorSchema<SszVectorT> {


  public SszByteVectorSchemaImpl(long vectorLength) {
    super(SszPrimitiveSchemas.BYTE_SCHEMA, vectorLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT createFromBackingNode(TreeNode node) {
    return (SszVectorT) new SszByteVectorImpl(this, () -> node);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT fromBytes(Bytes bytes) {
    return (SszVectorT) new SszByteVectorImpl(this, bytes);
  }

}
