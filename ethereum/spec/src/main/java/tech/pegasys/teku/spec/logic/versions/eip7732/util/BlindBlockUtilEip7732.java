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

package tech.pegasys.teku.spec.logic.versions.eip7732.util;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.SignedBeaconBlockBlinderEip7732;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732.SignedBeaconBlockUnblinderEip7732;
import tech.pegasys.teku.spec.logic.common.util.BlindBlockUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7732;

public class BlindBlockUtilEip7732 extends BlindBlockUtil {

  private final SchemaDefinitionsEip7732 schemaDefinitions;

  public BlindBlockUtilEip7732(final SchemaDefinitionsEip7732 schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
  }

  @Override
  protected SignedBeaconBlockUnblinder createSignedBeaconBlockUnblinder(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    return new SignedBeaconBlockUnblinderEip7732(schemaDefinitions, signedBlindedBeaconBlock);
  }

  @Override
  protected SignedBeaconBlockBlinder getSignedBeaconBlockBlinder() {
    return new SignedBeaconBlockBlinderEip7732(schemaDefinitions);
  }
}
