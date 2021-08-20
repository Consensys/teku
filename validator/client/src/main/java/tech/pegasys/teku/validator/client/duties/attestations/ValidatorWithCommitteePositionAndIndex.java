/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.duties.attestations;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.validator.client.Validator;

class ValidatorWithCommitteePositionAndIndex {

  private final Validator validator;
  private final int committeePosition;
  private final int validatorIndex;
  private final int committeeSize;

  ValidatorWithCommitteePositionAndIndex(
      final Validator validator,
      final int committeePosition,
      final int validatorIndex,
      final int committeeSize) {
    this.validator = validator;
    this.committeePosition = committeePosition;
    this.validatorIndex = validatorIndex;
    this.committeeSize = committeeSize;
  }

  public Signer getSigner() {
    return validator.getSigner();
  }

  public int getCommitteePosition() {
    return committeePosition;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public int getCommitteeSize() {
    return committeeSize;
  }

  public BLSPublicKey getPublicKey() {
    return validator.getPublicKey();
  }

  @Override
  public String toString() {
    return "ValidatorWithCommitteePositionAndIndex{"
        + "validator="
        + validator
        + ", committeePosition="
        + committeePosition
        + ", validatorIndex="
        + validatorIndex
        + '}';
  }
}
