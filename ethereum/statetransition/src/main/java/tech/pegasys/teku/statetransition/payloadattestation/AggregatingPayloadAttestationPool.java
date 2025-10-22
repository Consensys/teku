/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.payloadattestation;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

// TODO-GLOAS: https://github.com/Consensys/teku/issues/10041
public class AggregatingPayloadAttestationPool implements PayloadAttestationPool {

  private final Spec spec;
  private final PayloadAttestationMessageValidator validator;

  public AggregatingPayloadAttestationPool(
      final Spec spec, final PayloadAttestationMessageValidator validator) {
    this.spec = spec;
    this.validator = validator;
  }

  @Override
  public void onSlot(final UInt64 slot) {}

  @Override
  public void subscribeOperationAdded(
      final OperationAddedSubscriber<PayloadAttestationMessage> subscriber) {}

  @Override
  public SafeFuture<InternalValidationResult> addLocal(
      final PayloadAttestationMessage payloadAttestationMessage) {
    return validator.validate(payloadAttestationMessage);
  }

  @Override
  public SafeFuture<InternalValidationResult> addRemote(
      final PayloadAttestationMessage payloadAttestationMessage,
      final Optional<UInt64> arrivalTimestamp) {
    return validator.validate(payloadAttestationMessage);
  }

  @Override
  public SszList<PayloadAttestation> getPayloadAttestationsForBlock(
      final BeaconState blockSlotState, final Bytes32 parentRoot) {
    final SszListSchema<PayloadAttestation, ?> payloadAttestationsSchema =
        BeaconBlockBodySchemaGloas.required(
                spec.atSlot(blockSlotState.getSlot())
                    .getSchemaDefinitions()
                    .getBeaconBlockBodySchema())
            .getPayloadAttestationsSchema();
    return payloadAttestationsSchema.of();
  }
}
