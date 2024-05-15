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

package tech.pegasys.teku.validator.client.duties.attestations;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.validator.client.Validator;

record ValidatorWithAttestationDutyInfo(
    Validator validator,
    int committeeIndex,
    int committeePosition,
    int validatorIndex,
    int committeeSize) {

  public Signer signer() {
    return validator.getSigner();
  }

  public BLSPublicKey publicKey() {
    return validator.getPublicKey();
  }
}
