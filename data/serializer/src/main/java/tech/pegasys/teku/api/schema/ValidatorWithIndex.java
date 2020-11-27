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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.util.ValidatorsUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorWithIndex {
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  public final BLSPubKey pubkey;

  public final Integer validator_index;

  @Schema(type = "string", format = "uint64")
  public final UInt64 balance;

  public final Validator validator;

  @JsonCreator
  public ValidatorWithIndex(
      @JsonProperty("pubkey") final BLSPubKey pubkey,
      @JsonProperty("validator_index") final Integer validator_index,
      @JsonProperty("balance") final UInt64 balance,
      @JsonProperty("validator") final Validator validator) {
    this.pubkey = pubkey;
    this.validator_index = validator_index;
    this.balance = balance;
    this.validator = validator;
  }

  public ValidatorWithIndex(
      final Validator validator, final int validator_index, final UInt64 balance) {
    this.validator = validator;
    this.validator_index = validator_index;
    this.balance = balance;
    this.pubkey = validator.pubkey;
  }

  public ValidatorWithIndex(
      final tech.pegasys.teku.datastructures.state.Validator validator,
      tech.pegasys.teku.datastructures.state.BeaconState state) {
    BLSPublicKey blsPublicKey = BLSPublicKey.fromBytesCompressed(validator.getPubkey());
    Optional<Integer> optionalInteger = ValidatorsUtil.getValidatorIndex(state, blsPublicKey);
    if (optionalInteger.isPresent()) {
      this.validator_index = optionalInteger.get();
      this.balance = state.getBalances().get(this.validator_index);
    } else {
      this.validator_index = null;
      this.balance = null;
    }
    this.validator = new Validator(validator);
    this.pubkey = new BLSPubKey(validator.getPubkey());
  }

  public ValidatorWithIndex(BLSPubKey pubkey) {
    this.pubkey = pubkey;
    this.balance = null;
    this.validator = null;
    this.validator_index = null;
  }
}
