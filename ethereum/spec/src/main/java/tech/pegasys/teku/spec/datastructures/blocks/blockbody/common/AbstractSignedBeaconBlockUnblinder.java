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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.common;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlindedBlockContainer;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public abstract class AbstractSignedBeaconBlockUnblinder implements SignedBeaconBlockUnblinder {

  protected final SignedBlindedBlockContainer signedBlindedBlockContainer;
  protected final SchemaDefinitions schemaDefinitions;

  public AbstractSignedBeaconBlockUnblinder(
      final SchemaDefinitions schemaDefinitions,
      final SignedBlindedBlockContainer signedBlindedBlockContainer) {
    this.schemaDefinitions = schemaDefinitions;
    this.signedBlindedBlockContainer = signedBlindedBlockContainer;
  }

  @Override
  public SignedBlindedBlockContainer getSignedBlindedBlockContainer() {
    return signedBlindedBlockContainer;
  }
}
