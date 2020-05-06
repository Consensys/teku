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

import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.state.Committee;

public class AttesterInformation {

  private final int validatorIndex;
  private final BLSPublicKey publicKey;
  private final int indexIntoCommittee;
  private final Committee committee;
  private final Optional<BLSSignature> selection_proof;

  public AttesterInformation(
      int validatorIndex,
      BLSPublicKey publicKey,
      int indexIntoCommittee,
      Committee committee,
      Optional<BLSSignature> selection_proof) {
    this.validatorIndex = validatorIndex;
    this.publicKey = publicKey;
    this.indexIntoCommittee = indexIntoCommittee;
    this.committee = committee;
    this.selection_proof = selection_proof;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public int getIndexIntoCommittee() {
    return indexIntoCommittee;
  }

  public Committee getCommittee() {
    return committee;
  }

  public Optional<BLSSignature> getSelection_proof() {
    return selection_proof;
  }
}
