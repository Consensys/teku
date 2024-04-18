/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.spec.datastructures.blobs.versions.electra;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class CellSchema extends SszByteVectorSchemaImpl<Cell> {

  public CellSchema(final SpecConfigElectra specConfig) {
    super(
        SpecConfigDeneb.BYTES_PER_FIELD_ELEMENT.longValue()
            * specConfig.getFieldElementsPerCell().longValue());
  }

  public Cell create(final Bytes bytes) {
    return new Cell(this, bytes);
  }

  @Override
  public Cell createFromBackingNode(TreeNode node) {
    return new Cell(this, node);
  }
}
