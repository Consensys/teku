/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class SignedBeaconBlockUnblinderEip7732 extends AbstractSignedBeaconBlockUnblinder {

  public SignedBeaconBlockUnblinderEip7732(
      final SchemaDefinitionsBellatrix schemaDefinitions,
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    super(schemaDefinitions, signedBlindedBeaconBlock);
  }

  @Override
  public void setExecutionPayloadSupplier(
      final Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
    // NO-OP
  }

  @Override
  public SafeFuture<SignedBeaconBlock> unblind() {
    return SafeFuture.completedFuture(
        signedBlindedBeaconBlock.unblind(schemaDefinitions, Optional.empty()));
  }
}
