/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.beaconchainblocks;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;

public class BeaconBlock {

  // Header
  private long slot;
  private Hash[] ancestor_hashes;
  private Hash state_root;
  private Hash randao_reveal;
  private Hash candidate_pow_receipt_root;
  private UInt384[] signature;

  // Body
  private BeaconBlockBody body;


  public BeaconBlock() {

  }

  /*********************
   *                   *
   * GETTERS & SETTERS *
   *                   *
   *********************/

  public BeaconBlockBody getBody() {
    return body;
  }

  public void setBody(BeaconBlockBody body) {
    this.body = body;
  }

  public UInt384[] getSignature() {
    return signature;
  }

  public void setSignature(UInt384[] signature) {
    this.signature = signature;
  }

  public Hash getCandidate_pow_receipt_root() {
    return candidate_pow_receipt_root;
  }

  public void setCandidate_pow_receipt_root(Hash candidate_pow_receipt_root) {
    this.candidate_pow_receipt_root = candidate_pow_receipt_root;
  }

  public Hash getRandao_reveal() {
    return randao_reveal;
  }

  public void setRandao_reveal(Hash randao_reveal) {
    this.randao_reveal = randao_reveal;
  }

  public Hash getState_root() {
    return state_root;
  }

  public void setState_root(Hash state_root) {
    this.state_root = state_root;
  }

  public Hash[] getAncestor_hashes() {
    return ancestor_hashes;
  }

  public void setAncestor_hashes(Hash[] ancestor_hashes) {
    this.ancestor_hashes = ancestor_hashes;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }
}
