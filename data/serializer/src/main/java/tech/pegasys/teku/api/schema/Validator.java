/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_PUBKEY;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_BYTES32;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_PUBKEY;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Validator {
  @Schema(
      type = "string",
      pattern = PATTERN_PUBKEY,
      example = EXAMPLE_PUBKEY,
      description = DESCRIPTION_BYTES48)
  public final BLSPubKey pubkey;

  @Schema(
      type = "string",
      pattern = PATTERN_BYTES32,
      example = EXAMPLE_BYTES32,
      description = "Root of withdrawal credentials")
  public final Bytes32 withdrawal_credentials;

  @Schema(type = "string", example = EXAMPLE_UINT64, description = "Balance at stake in Gwei.")
  public final UInt64 effective_balance;

  public final boolean slashed;

  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "When criteria for activation were met.")
  public final UInt64 activation_eligibility_epoch;

  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Epoch when validator activated. 'FAR_FUTURE_EPOCH' if not activated.")
  public final UInt64 activation_epoch;

  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description = "Epoch when validator exited. 'FAR_FUTURE_EPOCH' if not exited.")
  public final UInt64 exit_epoch;

  @Schema(
      type = "string",
      example = EXAMPLE_UINT64,
      description =
          "When validator can withdraw or transfer funds. 'FAR_FUTURE_EPOCH' if not defined.")
  public final UInt64 withdrawable_epoch;

  @JsonCreator
  public Validator(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("withdrawal_credentials") final Bytes32 withdrawal_credentials,
      @JsonProperty("effective_balance") final UInt64 effective_balance,
      @JsonProperty("slashed") final boolean slashed,
      @JsonProperty("activation_eligibility_epoch") final UInt64 activation_eligibility_epoch,
      @JsonProperty("activation_epoch") final UInt64 activation_epoch,
      @JsonProperty("exit_epoch") final UInt64 exit_epoch,
      @JsonProperty("withdrawable_epoch") final UInt64 withdrawable_epoch) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.effective_balance = effective_balance;
    this.slashed = slashed;
    this.activation_eligibility_epoch = activation_eligibility_epoch;
    this.activation_epoch = activation_epoch;
    this.exit_epoch = exit_epoch;
    this.withdrawable_epoch = withdrawable_epoch;
  }

  public Validator(final tech.pegasys.teku.datastructures.state.Validator validator) {
    this.pubkey = new BLSPubKey(validator.getPubkey());
    this.withdrawal_credentials = validator.getWithdrawal_credentials();
    this.effective_balance = validator.getEffective_balance();
    this.slashed = validator.isSlashed();
    this.activation_eligibility_epoch = validator.getActivation_eligibility_epoch();
    this.activation_epoch = validator.getActivation_epoch();
    this.exit_epoch = validator.getExit_epoch();
    this.withdrawable_epoch = validator.getWithdrawable_epoch();
  }

  public tech.pegasys.teku.datastructures.state.Validator asInternalValidator() {
    return tech.pegasys.teku.datastructures.state.Validator.create(
        pubkey.asBLSPublicKey().toBytesCompressed(),
        withdrawal_credentials,
        effective_balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Validator validator = (Validator) o;
    return slashed == validator.slashed
        && Objects.equals(pubkey, validator.pubkey)
        && Objects.equals(withdrawal_credentials, validator.withdrawal_credentials)
        && Objects.equals(effective_balance, validator.effective_balance)
        && Objects.equals(activation_eligibility_epoch, validator.activation_eligibility_epoch)
        && Objects.equals(activation_epoch, validator.activation_epoch)
        && Objects.equals(exit_epoch, validator.exit_epoch)
        && Objects.equals(withdrawable_epoch, validator.withdrawable_epoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        pubkey,
        withdrawal_credentials,
        effective_balance,
        slashed,
        activation_eligibility_epoch,
        activation_epoch,
        exit_epoch,
        withdrawable_epoch);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubkey", pubkey)
        .add("withdrawal_credentials", withdrawal_credentials)
        .add("effective_balance", effective_balance)
        .add("slashed", slashed)
        .add("activation_eligibility_epoch", activation_eligibility_epoch)
        .add("activation_epoch", activation_epoch)
        .add("exit_epoch", exit_epoch)
        .add("withdrawable_epoch", withdrawable_epoch)
        .toString();
  }
}
