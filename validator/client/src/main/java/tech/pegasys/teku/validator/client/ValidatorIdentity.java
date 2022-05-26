/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.validator.client;

import java.util.Objects;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorIdentity {

  private final Bytes20 feeRecipient;
  private final UInt64 gasLimit;
  private final BLSPublicKey publicKey;

  public ValidatorIdentity(
      final Bytes20 feeRecipient, final UInt64 gasLimit, final BLSPublicKey publicKey) {
    this.feeRecipient = feeRecipient;
    this.gasLimit = gasLimit;
    this.publicKey = publicKey;
  }

  public Bytes20 getFeeRecipient() {
    return feeRecipient;
  }

  public UInt64 getGasLimit() {
    return gasLimit;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ValidatorIdentity that = (ValidatorIdentity) o;
    return Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(gasLimit, that.gasLimit)
        && Objects.equals(publicKey, that.publicKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(feeRecipient, gasLimit, publicKey);
  }
}
