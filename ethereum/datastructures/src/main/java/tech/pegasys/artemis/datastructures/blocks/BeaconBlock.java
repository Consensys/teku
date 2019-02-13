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
import java.util.Objects;
import java.util.stream.Collectors;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.datastructures.operations.BLSSignature;

public final class BeaconBlock {

  // Header
  private long slot;
  private Bytes32 parent_root;
  private Bytes32 state_root;
  private List<Bytes48> randao_reveal;
  private Eth1Data eth1_data;
  private BLSSignature signature;

  // Body
  private BeaconBlockBody body;

  public BeaconBlock(
      long slot,
      Bytes32 parent_root,
      Bytes32 state_root,
      List<Bytes48> randao_reveal,
      Eth1Data eth1_data,
      BLSSignature signature,
      BeaconBlockBody body) {
    this.slot = slot;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.randao_reveal = randao_reveal;
    this.eth1_data = eth1_data;
    this.signature = signature;
    this.body = body;
  }

  public static BeaconBlock fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BeaconBlock(
                reader.readUInt64(),
                Bytes32.wrap(reader.readBytes()),
                Bytes32.wrap(reader.readBytes()),
                reader.readBytesList().stream().map(Bytes48::wrap).collect(Collectors.toList()),
                Eth1Data.fromBytes(reader.readBytes()),
                BLSSignature.fromBytes(reader.readBytes()),
                BeaconBlockBody.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(slot);
          writer.writeBytes(parent_root);
          writer.writeBytes(state_root);
          writer.writeBytesList(randao_reveal);
          writer.writeBytes(eth1_data.toBytes());
          writer.writeBytes(signature.toBytes());
          writer.writeBytes(body.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, parent_root, state_root, randao_reveal, eth1_data, signature);
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
        && Objects.equals(this.getSignature(), other.getSignature())
        && Objects.equals(this.getBody(), other.getBody());
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
}
