package tech.pegasys.teku.spec.datastructures.blobs.versions.electra;

import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class DataColumnSchema
    extends AbstractSszListSchema<Cell, DataColumn> {

  public DataColumnSchema(final SpecConfigElectra specConfig) {
    super(new CellSchema(specConfig), specConfig.getMaxBlobCommitmentsPerBlock());
  }

  @Override
  public DataColumn createFromBackingNode(TreeNode node) {
    return new DataColumn(this, node);
  }
}
