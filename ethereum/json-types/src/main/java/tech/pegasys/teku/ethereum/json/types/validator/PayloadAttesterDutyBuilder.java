/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.ethereum.json.types.validator;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PayloadAttesterDutyBuilder {
  public static final DeserializableTypeDefinition<PayloadAttesterDuty> PAYLOAD_ATTESTER_DUTY_TYPE =
      DeserializableTypeDefinition.object(
              PayloadAttesterDuty.class, PayloadAttesterDutyBuilder.class)
          .name("AttesterDuty")
          .initializer(PayloadAttesterDutyBuilder::new)
          .finisher(PayloadAttesterDutyBuilder::build)
          .withField(
              "pubkey",
              PUBLIC_KEY_TYPE,
              PayloadAttesterDuty::publicKey,
              PayloadAttesterDutyBuilder::publicKey)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              PayloadAttesterDuty::validatorIndex,
              PayloadAttesterDutyBuilder::validatorIndex)
          .withField(
              "slot", UINT64_TYPE, PayloadAttesterDuty::slot, PayloadAttesterDutyBuilder::slot)
          .build();

  private BLSPublicKey publicKey;
  private int validatorIndex;
  private UInt64 slot;

  public PayloadAttesterDutyBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public PayloadAttesterDutyBuilder validatorIndex(final int validatorIndex) {
    this.validatorIndex = validatorIndex;
    return this;
  }

  public PayloadAttesterDutyBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  public PayloadAttesterDuty build() {
    return new PayloadAttesterDuty(publicKey, validatorIndex, slot);
  }
}
