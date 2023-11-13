/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.blobs.versions.deneb;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitmentSchema;

public class BlobKzgCommitmentsSchema
    extends AbstractSszListSchema<SszKZGCommitment, SszList<SszKZGCommitment>> {

  public BlobKzgCommitmentsSchema(final SpecConfigDeneb specConfig) {
    super(SszKZGCommitmentSchema.INSTANCE, specConfig.getMaxBlobCommitmentsPerBlock());
  }

  @Override
  public SszList<SszKZGCommitment> createFromBackingNode(final TreeNode node) {
    return new SszListImpl<>(this, node);
  }

  public SszList<SszKZGCommitment> createFromBlobsBundle(final BlobsBundle blobsBundle) {
    return createFromElements(
        blobsBundle.getCommitments().stream().map(SszKZGCommitment::new).toList());
  }
}
