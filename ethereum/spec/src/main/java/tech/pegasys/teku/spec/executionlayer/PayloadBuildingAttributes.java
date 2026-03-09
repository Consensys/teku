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

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;

public class PayloadBuildingAttributes {

  private final UInt64 proposerIndex;
  private final UInt64 proposalSlot;
  private final UInt64 timestamp;
  private final Bytes32 prevRandao;
  private final Eth1Address feeRecipient;
  private final Optional<SignedValidatorRegistration> validatorRegistration;
  private final Optional<List<Withdrawal>> withdrawals;
  private final Bytes32 parentBeaconBlockRoot;

  public PayloadBuildingAttributes(
      final UInt64 proposerIndex,
      final UInt64 proposalSlot,
      final UInt64 timestamp,
      final Bytes32 prevRandao,
      final Eth1Address feeRecipient,
      final Optional<SignedValidatorRegistration> validatorRegistration,
      final Optional<List<Withdrawal>> withdrawals,
      final Bytes32 parentBeaconBlockRoot) {
    this.proposerIndex = proposerIndex;
    this.proposalSlot = proposalSlot;
    this.timestamp = timestamp;
    this.prevRandao = prevRandao;
    this.feeRecipient = feeRecipient;
    this.validatorRegistration = validatorRegistration;
    this.withdrawals = withdrawals;
    this.parentBeaconBlockRoot = parentBeaconBlockRoot;
  }

  public UInt64 getProposerIndex() {
    return proposerIndex;
  }

  public UInt64 getProposalSlot() {
    return proposalSlot;
  }

  public UInt64 getTimestamp() {
    return timestamp;
  }

  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  public Eth1Address getFeeRecipient() {
    return feeRecipient;
  }

  public Bytes32 getParentBeaconBlockRoot() {
    return parentBeaconBlockRoot;
  }

  public Optional<SignedValidatorRegistration> getValidatorRegistration() {
    return validatorRegistration;
  }

  public Optional<BLSPublicKey> getValidatorRegistrationPublicKey() {
    return validatorRegistration.map(
        signedValidatorRegistration -> signedValidatorRegistration.getMessage().getPublicKey());
  }

  public Optional<List<Withdrawal>> getWithdrawals() {
    return withdrawals;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PayloadBuildingAttributes that = (PayloadBuildingAttributes) o;
    return Objects.equals(proposerIndex, that.proposerIndex)
        && Objects.equals(proposalSlot, that.proposalSlot)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(prevRandao, that.prevRandao)
        && Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(validatorRegistration, that.validatorRegistration)
        && Objects.equals(withdrawals, that.withdrawals)
        && Objects.equals(parentBeaconBlockRoot, that.parentBeaconBlockRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        proposerIndex,
        proposalSlot,
        timestamp,
        prevRandao,
        feeRecipient,
        validatorRegistration,
        withdrawals,
        parentBeaconBlockRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("proposerIndex", proposerIndex)
        .add("proposalSlot", proposalSlot)
        .add("timestamp", timestamp)
        .add("prevRandao", prevRandao)
        .add("feeRecipient", feeRecipient)
        .add("validatorRegistration", validatorRegistration)
        .add("withdrawals", withdrawals)
        .add("parentBeaconBlockRoot", parentBeaconBlockRoot)
        .toString();
  }
}
