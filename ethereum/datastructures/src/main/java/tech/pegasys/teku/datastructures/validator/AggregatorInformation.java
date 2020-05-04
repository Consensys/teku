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

package tech.pegasys.teku.datastructures.validator;

import tech.pegasys.teku.bls.BLSSignature;

public class AggregatorInformation {

  private final BLSSignature selection_proof;
  private final int validatorIndex;

  public AggregatorInformation(BLSSignature selection_proof, int validatorIndex) {
    this.selection_proof = selection_proof;
    this.validatorIndex = validatorIndex;
  }

  public BLSSignature getSelection_proof() {
    return selection_proof;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }
}
