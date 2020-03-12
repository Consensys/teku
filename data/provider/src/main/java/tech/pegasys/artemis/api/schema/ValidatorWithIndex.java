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

package tech.pegasys.artemis.api.schema;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;

public class ValidatorWithIndex {
  public final BLSPubKey pubkey;
  public final Integer validator_index;
  public final UnsignedLong balance;
  public final Validator validator;

  public ValidatorWithIndex(
      final Validator validator, final int validator_index, final UnsignedLong balance) {
    this.validator = validator;
    this.validator_index = validator_index;
    this.balance = balance;
    this.pubkey = validator.pubkey;
  }

  public ValidatorWithIndex(final Validator validator, BeaconState state) {
    Optional<Validator> val =
        state.validators.stream().filter(v -> v.pubkey.equals(validator.pubkey)).findFirst();
    if (val.isPresent()) {
      this.validator_index = state.validators.indexOf(val.get());
      this.balance = state.balances.get(this.validator_index);
    } else {
      this.validator_index = null;
      this.balance = null;
    }
    this.validator = validator;
    this.pubkey = validator.pubkey;
  }

  public ValidatorWithIndex(BLSPubKey pubkey) {
    this.pubkey = pubkey;
    this.balance = null;
    this.validator = null;
    this.validator_index = null;
  }
}
