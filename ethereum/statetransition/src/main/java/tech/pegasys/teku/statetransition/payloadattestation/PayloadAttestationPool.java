/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface PayloadAttestationPool {

  PayloadAttestationPool NOOP =
      new PayloadAttestationPool() {
        @Override
        public void subscribeOperationAdded(
            final OperationAddedSubscriber<PayloadAttestationMessage> subscriber) {}

        @Override
        public SafeFuture<InternalValidationResult> addLocal(
            final PayloadAttestationMessage payloadAttestationMessage) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<InternalValidationResult> addRemote(
            final PayloadAttestationMessage payloadAttestationMessage,
            final Optional<UInt64> arrivalTimestamp) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SszList<PayloadAttestation> getPayloadAttestationsForBlock(
            final BeaconState blockSlotState, final Bytes32 parentRoot) {
          return null;
        }
      };

  void subscribeOperationAdded(OperationAddedSubscriber<PayloadAttestationMessage> subscriber);

  SafeFuture<InternalValidationResult> addLocal(
      PayloadAttestationMessage payloadAttestationMessage);

  SafeFuture<InternalValidationResult> addRemote(
      PayloadAttestationMessage payloadAttestationMessage, Optional<UInt64> arrivalTimestamp);

  SszList<PayloadAttestation> getPayloadAttestationsForBlock(
      BeaconState blockSlotState, Bytes32 parentRoot);
}
