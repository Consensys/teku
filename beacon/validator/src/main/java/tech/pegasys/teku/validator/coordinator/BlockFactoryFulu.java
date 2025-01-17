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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

public class BlockFactoryFulu extends BlockFactoryDeneb {
  private final KZG kzg;

  public BlockFactoryFulu(
      final Spec spec, final BlockOperationSelectorFactory operationSelector, final KZG kzg) {
    super(spec, operationSelector);
    this.kzg = kzg;
  }

  @Override
  public List<DataColumnSidecar> createDataColumnSidecars(
      final SignedBlockContainer blockContainer, final List<Blob> blobs) {
    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.atSlot(blockContainer.getSlot()).miscHelpers());
    return miscHelpersFulu.constructDataColumnSidecars(blockContainer.getSignedBlock(), blobs, kzg);
  }
}
