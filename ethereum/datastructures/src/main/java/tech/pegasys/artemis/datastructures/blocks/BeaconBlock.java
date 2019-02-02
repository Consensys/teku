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

package tech.pegasys.artemis.datastructures.blocks;

import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;

public final class BeaconBlock {

  // Header
  private long slot;
  private List<Bytes32> ancestor_hashes;
  private Bytes32 state_root;
  private List<Bytes48> randao_reveal;
  private Bytes32 candidate_pow_receipt_root;
  private List<Bytes48> signature;

  // Body
  private BeaconBlockBody body;

  public BeaconBlock(
      long slot,
      List<Bytes32> ancestor_hashes,
      Bytes32 state_root,
      List<Bytes48> randao_reveal,
      Bytes32 candidate_pow_receipt_root,
      List<Bytes48> signature,
      BeaconBlockBody body) {
    this.slot = slot;
    this.ancestor_hashes = ancestor_hashes;
    this.state_root = state_root;
    this.randao_reveal = randao_reveal;
    this.candidate_pow_receipt_root = candidate_pow_receipt_root;
    this.signature = signature;
    this.body = body;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot);
          writer.writeBytesList(ancestor_hashes.toArray(new Bytes32[0]));
          writer.writeBytes(state_root);
          writer.writeBytesList(randao_reveal.toArray(new Bytes48[0]));
          writer.writeBytes(candidate_pow_receipt_root);
          writer.writeBytesList(signature.toArray(new Bytes48[0]));
          writer.writeBytes(body.toBytes());
        });
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BeaconBlockBody getBody() {
    return body;
  }

  public void setBody(BeaconBlockBody body) {
    this.body = body;
  }

  public List<Bytes48> getSignature() {
    return signature;
  }

  public void setSignature(List<Bytes48> signature) {
    this.signature = signature;
  }

  public Bytes32 getCandidate_pow_receipt_root() {
    return candidate_pow_receipt_root;
  }

  public void setCandidate_pow_receipt_root(Bytes32 candidate_pow_receipt_root) {
    this.candidate_pow_receipt_root = candidate_pow_receipt_root;
  }

  public List<Bytes48> getRandao_reveal() {
    return randao_reveal;
  }

  public void setRandao_reveal(List<Bytes48> randao_reveal) {
    this.randao_reveal = randao_reveal;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public List<Bytes32> getAncestor_hashes() {
    return ancestor_hashes;
  }

  public void setAncestor_hashes(List<Bytes32> ancestor_hashes) {
    this.ancestor_hashes = ancestor_hashes;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }
}
