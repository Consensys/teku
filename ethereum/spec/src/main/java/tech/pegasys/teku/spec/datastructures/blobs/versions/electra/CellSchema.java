package tech.pegasys.teku.spec.datastructures.blobs.versions.electra;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class CellSchema extends SszByteVectorSchemaImpl<Cell> {

  public CellSchema(final SpecConfigElectra specConfig) {
    super(SpecConfigDeneb.BYTES_PER_FIELD_ELEMENT.longValue() * specConfig.getFieldElementsPerCell().longValue());
  }

  public Cell create(final Bytes bytes) {
    return new Cell(this, bytes);
  }

  @Override
  public Cell createFromBackingNode(TreeNode node) {
    return new Cell(this, node);
  }
}
