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

package tech.pegasys.artemis.validator.coordinator;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class ValidatorInfo {

  private final MessageSignerService signerService;
  private int validatorIndex = -1;

  public ValidatorInfo(final MessageSignerService signerService) {
    this.signerService = signerService;
  }

  public MessageSignerService getSignerService() {
    return signerService;
  }

  public SafeFuture<BLSSignature> sign(final Bytes message) {
    return signerService.sign(message);
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public void setValidatorIndex(int validatorIndex) {
    this.validatorIndex = validatorIndex;
  }
}
