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

package tech.pegasys.teku.spec.executionlayer;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;

public record PayloadBuildingAttributes(
    UInt64 proposerIndex,
    UInt64 proposalSlot,
    UInt64 timestamp,
    Bytes32 prevRandao,
    Eth1Address feeRecipient,
    Optional<SignedValidatorRegistration> validatorRegistration,
    Optional<List<Withdrawal>> withdrawals,
    ForkChoiceNode parentBeaconBlock) {

  public Optional<BLSPublicKey> getValidatorRegistrationPublicKey() {
    return validatorRegistration.map(
        signedValidatorRegistration -> signedValidatorRegistration.getMessage().getPublicKey());
  }
}
