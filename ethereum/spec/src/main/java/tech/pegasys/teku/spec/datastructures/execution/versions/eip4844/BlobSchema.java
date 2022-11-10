/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszByteVectorSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;

public class BlobSchema extends SszByteVectorSchemaImpl<Blob> {

  public BlobSchema(final SpecConfigEip4844 specConfig) {
    super(
        SszPrimitiveSchemas.BYTE_SCHEMA,
        SpecConfigEip4844.BYTES_PER_FIELD_ELEMENT
            .times(specConfig.getFieldElementsPerBlob())
            .longValue());
  }

  @Override
  public Blob createFromBackingNode(TreeNode node) {
    return new Blob(this, node);
  }
}
