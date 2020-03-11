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

public class ValidatorDuties {
  public final int committeeIndex;
  public final BLSPubKey publicKey;
  public final int validatorIndex;

  public ValidatorDuties(int committeeIndex, BLSPubKey publicKey, int validatorIndex) {
    this.committeeIndex = committeeIndex;
    this.publicKey = publicKey;
    this.validatorIndex = validatorIndex;
  }

  public static ValidatorDuties empty() {
    return new ValidatorDuties(0, null, 0);
  }
}
