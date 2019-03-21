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

import static tech.pegasys.artemis.datastructures.Constants.EMPTY_SIGNATURE;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.datastructures.Constants.ZERO_HASH;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public final class BeaconBlock {

  // Header
  private long slot;
  private Bytes32 parent_root;
  private Bytes32 state_root;
  private BLSSignature randao_reveal;
  private Eth1Data eth1_data;

  // Body
  private BeaconBlockBody body;

  // Signature
  private BLSSignature signature;

  public BeaconBlock(
      long slot,
      Bytes32 parent_root,
      Bytes32 state_root,
      BLSSignature randao_reveal,
      Eth1Data eth1_data,
      BeaconBlockBody body,
      BLSSignature signature) {
    this.slot = slot;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.randao_reveal = randao_reveal;
    this.eth1_data = eth1_data;
    this.body = body;
    this.signature = signature;
  }

  public static BeaconBlock createGenesis(Bytes32 state_root) {
    return new BeaconBlock(
        GENESIS_SLOT,
        ZERO_HASH,
        state_root,
        EMPTY_SIGNATURE,
        new Eth1Data(ZERO_HASH, ZERO_HASH),
        new BeaconBlockBody(
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>()),
        EMPTY_SIGNATURE);
  }

  public static BeaconBlock fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlock(
                reader.readUInt64(),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes()),
                Eth1Data.fromBytes(reader.readBytes()),
                BeaconBlockBody.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot);
          writer.writeBytes(parent_root);
          writer.writeBytes(state_root);
          writer.writeBytes(randao_reveal.toBytes());
          writer.writeBytes(eth1_data.toBytes());
          writer.writeBytes(body.toBytes());
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, parent_root, state_root, randao_reveal, eth1_data, body, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlock)) {
      return false;
    }

    BeaconBlock other = (BeaconBlock) obj;
    return slot == other.getSlot()
        && Objects.equals(this.getParent_root(), other.getParent_root())
        && Objects.equals(this.getState_root(), other.getState_root())
        && Objects.equals(this.getRandao_reveal(), other.getRandao_reveal())
        && Objects.equals(this.getEth1_data(), other.getEth1_data())
        && Objects.equals(this.getBody(), other.getBody())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BeaconBlockBody getBody() {
    return body;
  }

  public void setBody(BeaconBlockBody body) {
    this.body = body;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public Eth1Data getEth1_data() {
    return eth1_data;
  }

  public void setEth1_data(Eth1Data eth1_data) {
    this.eth1_data = eth1_data;
  }

  public BLSSignature getRandao_reveal() {
    return randao_reveal;
  }

  public void setRandao_reveal(BLSSignature randao_reveal) {
    this.randao_reveal = randao_reveal;
  }

  public Bytes32 getState_root() {
    return state_root;
  }

  public void setState_root(Bytes32 state_root) {
    this.state_root = state_root;
  }

  public Bytes32 getParent_root() {
    return parent_root;
  }

  public void setParent_root(Bytes32 parent_root) {
    this.parent_root = parent_root;
  }

  public long getSlot() {
    return slot;
  }

  public void setSlot(long slot) {
    this.slot = slot;
  }

  public Bytes32 signedRoot(String truncationParam) {
    if (!truncationParam.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(beaconBlock, \"signature\") is currently supported for type BeaconBlock.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleHash(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(
                    SSZ.encode(
                        writer -> {
                          writer.writeUInt64(slot);
                        })),
                HashTreeUtil.hash_tree_root(parent_root),
                HashTreeUtil.hash_tree_root(state_root),
                HashTreeUtil.hash_tree_root(randao_reveal.toBytes()),
                HashTreeUtil.hash_tree_root(eth1_data.toBytes()),
                HashTreeUtil.hash_tree_root(body.toBytes()))));
  }
}
