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

package tech.pegasys.artemis.datastructures.beaconchainstate;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;

public class CandidatePoWReceiptRootRecord {

  private Bytes32 candidate_pow_receipt_root;
  private UnsignedLong votes;

  public CandidatePoWReceiptRootRecord(Bytes32 candidate_pow_receipt_root, UnsignedLong votes) {
    this.candidate_pow_receipt_root = candidate_pow_receipt_root;
    this.votes = votes;
  }

  public Bytes32 getCandidate_pow_receipt_root() {
    return candidate_pow_receipt_root;
  }

  public void setCandidate_pow_receipt_root(Bytes32 candidate_pow_receipt_root) {
    this.candidate_pow_receipt_root = candidate_pow_receipt_root;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public UnsignedLong getVotes() {
    return votes;
  }

  public void setVotes(UnsignedLong votes) {
    this.votes = votes;
  }
}
