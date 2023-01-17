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
  private final UInt64 timestamp;
  private final Bytes32 prevRandao;
  private final Eth1Address feeRecipient;
  private final Optional<SignedValidatorRegistration> validatorRegistration;

  private final Optional<List<Withdrawal>> maybeWithdrawals;

  private final UInt64 blockSlot;

  public PayloadBuildingAttributes(
      final UInt64 timestamp,
      final Bytes32 prevRandao,
      final Eth1Address feeRecipient,
      final Optional<SignedValidatorRegistration> validatorRegistration,
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final UInt64 blockSlot) {
    this.timestamp = timestamp;
    this.prevRandao = prevRandao;
    this.feeRecipient = feeRecipient;
    this.validatorRegistration = validatorRegistration;
    this.maybeWithdrawals = maybeWithdrawals;
    this.blockSlot = blockSlot;
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

  public UInt64 getBlockSlot() {
    return blockSlot;
  }

  public Optional<SignedValidatorRegistration> getValidatorRegistration() {
    return validatorRegistration;
  }

  public Optional<BLSPublicKey> getValidatorRegistrationPublicKey() {
    return validatorRegistration.map(
        signedValidatorRegistration -> signedValidatorRegistration.getMessage().getPublicKey());
  }

  public Optional<UInt64> getValidatorRegistrationGasLimit() {
    return validatorRegistration.map(
        signedValidatorRegistration -> signedValidatorRegistration.getMessage().getGasLimit());
  }

  public Optional<List<Withdrawal>> getWithdrawals() {
    return maybeWithdrawals;
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
    return Objects.equals(timestamp, that.timestamp)
        && Objects.equals(prevRandao, that.prevRandao)
        && Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(validatorRegistration, that.validatorRegistration)
        && Objects.equals(maybeWithdrawals, that.maybeWithdrawals);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, prevRandao, feeRecipient, validatorRegistration);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("prevRandao", prevRandao)
        .add("feeRecipient", feeRecipient)
        .add("validatorRegistration", validatorRegistration)
        .add("withdrawals", maybeWithdrawals)
        .toString();
  }
}
