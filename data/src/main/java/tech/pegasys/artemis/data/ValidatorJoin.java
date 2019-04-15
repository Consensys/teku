/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.data;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.datastructures.state.Validator;

public class ValidatorJoin {
  Validator validator;
  UnsignedLong balance;

  public ValidatorJoin() {}

  public ValidatorJoin(Validator validator, UnsignedLong balance) {
    this.validator = validator;
    this.balance = balance;
  }

  public Validator getValidator() {
    return validator;
  }

  public void setValidator(Validator validator) {
    this.validator = validator;
  }

  public UnsignedLong getBalance() {
    return balance;
  }

  public void setBalance(UnsignedLong balance) {
    this.balance = balance;
  }
}
