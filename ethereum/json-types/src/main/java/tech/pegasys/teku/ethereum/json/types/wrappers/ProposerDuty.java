/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.json.types.wrappers;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerDuty {
  public static final DeserializableTypeDefinition<ProposerDuty> PROPOSER_DUTY_TYPE =
      DeserializableTypeDefinition.object(
              ProposerDuty.class, ProposerDuty.ProposerDutyBuilder.class)
          .initializer(ProposerDuty.ProposerDutyBuilder::new)
          .finisher(ProposerDuty.ProposerDutyBuilder::build)
          .withField(
              "pubkey",
              PUBLIC_KEY_TYPE,
              ProposerDuty::getPublicKey,
              ProposerDuty.ProposerDutyBuilder::publicKey)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              ProposerDuty::getValidatorIndex,
              ProposerDuty.ProposerDutyBuilder::validatorIndex)
          .withField(
              "slot", UINT64_TYPE, ProposerDuty::getSlot, ProposerDuty.ProposerDutyBuilder::slot)
          .build();

  private final BLSPublicKey publicKey;
  private final int validatorIndex;
  private final UInt64 slot;

  public ProposerDuty(final BLSPublicKey publicKey, final int validatorIndex, final UInt64 slot) {
    this.publicKey = publicKey;
    this.validatorIndex = validatorIndex;
    this.slot = slot;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerDuty that = (ProposerDuty) o;
    return validatorIndex == that.validatorIndex
        && Objects.equals(publicKey, that.publicKey)
        && Objects.equals(slot, that.slot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKey, validatorIndex, slot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("validatorIndex", validatorIndex)
        .add("slot", slot)
        .toString();
  }

  public static final class ProposerDutyBuilder {
    private BLSPublicKey publicKey;
    private int validatorIndex;
    private UInt64 slot;

    public ProposerDutyBuilder publicKey(BLSPublicKey publicKey) {
      this.publicKey = publicKey;
      return this;
    }

    public ProposerDutyBuilder validatorIndex(int validatorIndex) {
      this.validatorIndex = validatorIndex;
      return this;
    }

    public ProposerDutyBuilder slot(UInt64 slot) {
      this.slot = slot;
      return this;
    }

    public ProposerDuty build() {
      return new ProposerDuty(publicKey, validatorIndex, slot);
    }
  }
}
