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

package tech.pegasys.artemis.datastructures.BeaconChainBlocks;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.uint.UInt384;
import tech.pegasys.artemis.util.uint.UInt64;

public class BeaconBlock {

  // Header
  private UInt64 slot;
  private Hash[] ancestor_hashes;
  private Hash state_root;
  private Hash randao_reveal;
  private Hash candidate_pow_receipt_root;
  private UInt384[] signature;

  // Body
  private BeaconBlockBody body;


  public BeaconBlock() {

  }

  /**
   * @return the body
   */
  public BeaconBlockBody getBody() {
    return body;
  }

  /**
   * @param body the body to set
   */
  public void setBody(BeaconBlockBody body) {
    this.body = body;
  }

  /**
   * @return the signature
   */
  public UInt384[] getSignature() {
    return signature;
  }

  /**
   * @param signature the signature to set
   */
  public void setSignature(UInt384[] signature) {
    this.signature = signature;
  }

  /**
   * @return the candidate_pow_receipt_root
   */
  public Hash getCandidate_pow_receipt_root() {
    return candidate_pow_receipt_root;
  }

  /**
   * @param candidate_pow_receipt_root the candidate_pow_receipt_root to set
   */
  public void setCandidate_pow_receipt_root(Hash candidate_pow_receipt_root) {
    this.candidate_pow_receipt_root = candidate_pow_receipt_root;
  }

  /**
   * @return the randao_reveal
   */
  public Hash getRandao_reveal() {
    return randao_reveal;
  }

  /**
   * @param randao_reveal the randao_reveal to set
   */
  public void setRandao_reveal(Hash randao_reveal) {
    this.randao_reveal = randao_reveal;
  }

  /**
   * @return the state_root
   */
  public Hash getState_root() {
    return state_root;
  }

  /**
   * @param state_root the state_root to set
   */
  public void setState_root(Hash state_root) {
    this.state_root = state_root;
  }

  /**
   * @return the ancestor_hashes
   */
  public Hash[] getAncestor_hashes() {
    return ancestor_hashes;
  }

  /**
   * @param ancestor_hashes the ancestor_hashes to set
   */
  public void setAncestor_hashes(Hash[] ancestor_hashes) {
    this.ancestor_hashes = ancestor_hashes;
  }

  /**
   * @return the slot
   */
  public UInt64 getSlot() {
    return slot;
  }

  /**
   * @param slot the slot to set
   */
  public void setSlot(UInt64 slot) {
    this.slot = slot;
  }
}
