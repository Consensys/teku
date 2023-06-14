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

package tech.pegasys.teku.spec.logic.versions.deneb.util;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blobs.SignedBlobSidecarsUnblinder;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlindedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecarsUnblinderDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockBlinderDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockUnblinderDeneb;
import tech.pegasys.teku.spec.logic.common.util.BlindBlockUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlindBlockUtilDeneb extends BlindBlockUtil {
  private final SchemaDefinitionsDeneb schemaDefinitions;
  private final SignedBeaconBlockBlinder signedBeaconBlockBlinder;

  public BlindBlockUtilDeneb(final SchemaDefinitionsDeneb schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
    this.signedBeaconBlockBlinder = new SignedBeaconBlockBlinderDeneb(schemaDefinitions);
  }

  @Override
  protected SignedBeaconBlockUnblinder createSignedBeaconBlockUnblinder(
      final SignedBlindedBlockContainer signedBlindedBlockContainer) {
    return new SignedBeaconBlockUnblinderDeneb(schemaDefinitions, signedBlindedBlockContainer);
  }

  @Override
  protected Optional<SignedBlobSidecarsUnblinder> createSignedBlobSidecarsUnblinder(
      final List<SignedBlindedBlobSidecar> signedBlindedBlobSidecars) {
    return Optional.of(
        new SignedBlobSidecarsUnblinderDeneb(schemaDefinitions, signedBlindedBlobSidecars));
  }

  @Override
  protected SignedBeaconBlockBlinder getSignedBeaconBlockBlinder() {
    return signedBeaconBlockBlinder;
  }
}
