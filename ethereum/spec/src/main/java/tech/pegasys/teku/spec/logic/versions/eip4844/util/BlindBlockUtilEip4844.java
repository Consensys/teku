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

package tech.pegasys.teku.spec.logic.versions.eip4844.util;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockBlinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockBlinderEip4844;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockUnblinderEip4844;
import tech.pegasys.teku.spec.logic.common.util.BlindBlockUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip4844;

public class BlindBlockUtilEip4844 extends BlindBlockUtil {
  private final SchemaDefinitionsEip4844 schemaDefinitions;
  private final SignedBeaconBlockBlinder signedBeaconBlockBlinder;

  public BlindBlockUtilEip4844(final SchemaDefinitionsEip4844 schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
    this.signedBeaconBlockBlinder = new SignedBeaconBlockBlinderEip4844(schemaDefinitions);
  }

  @Override
  protected SignedBeaconBlockUnblinder createSignedBeaconBlockUnblinder(
      final SignedBeaconBlock signedBeaconBlock) {
    return new SignedBeaconBlockUnblinderEip4844(schemaDefinitions, signedBeaconBlock);
  }

  @Override
  protected SignedBeaconBlockBlinder getSignedBeaconBlockBlinder() {
    return signedBeaconBlockBlinder;
  }
}
