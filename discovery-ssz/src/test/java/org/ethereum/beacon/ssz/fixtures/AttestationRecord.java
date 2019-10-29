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

package org.ethereum.beacon.ssz.fixtures;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Slot attestation data */
@SSZSerializable
public class AttestationRecord {

  // Shard ID
  @SSZ(type = SSZ.UInt24)
  private final int shardId;
  // List of block hashes that this signature is signing over that
  // are NOT part of the current chain, in order of oldest to newest
  @SSZ(type = SSZ.Hash32)
  private final List<byte[]> obliqueParentHashes;
  // Block hash in the shard that we are attesting to
  @SSZ(type = SSZ.Hash32)
  private final byte[] shardBlockHash;
  // Who is participating
  @SSZ(type = SSZ.Bytes)
  private final Bitfield attesterBitfield;

  @SSZ(type = SSZ.Hash32)
  private final byte[] justifiedBlockHash;
  // Slot number
  private long slot;
  // Last justified block
  private long justifiedSlot;
  // The actual signature
  private Sign.Signature aggregateSig;

  public AttestationRecord(
      int shardId,
      List<byte[]> obliqueParentHashes,
      byte[] shardBlockHash,
      Bitfield attesterBitfield,
      byte[] justifiedBlockHash,
      long slot,
      long justifiedSlot,
      Sign.Signature aggregateSig) {
    this.slot = slot;
    this.shardId = shardId;
    this.obliqueParentHashes = obliqueParentHashes;
    this.shardBlockHash = shardBlockHash;
    this.attesterBitfield = attesterBitfield;
    this.justifiedSlot = justifiedSlot;
    this.justifiedBlockHash = justifiedBlockHash;
    this.aggregateSig = aggregateSig;
  }

  public AttestationRecord(
      int shardId,
      List<byte[]> obliqueParentHashes,
      byte[] shardBlockHash,
      Bitfield attesterBitfield,
      byte[] justifiedBlockHash) {
    this.shardId = shardId;
    this.obliqueParentHashes = obliqueParentHashes;
    this.shardBlockHash = shardBlockHash;
    this.attesterBitfield = attesterBitfield;
    this.justifiedBlockHash = justifiedBlockHash;
  }

  public long getSlot() {
    return slot;
  }

  public int getShardId() {
    return shardId;
  }

  public List<byte[]> getObliqueParentHashes() {
    return obliqueParentHashes;
  }

  public byte[] getShardBlockHash() {
    return shardBlockHash;
  }

  public Bitfield getAttesterBitfield() {
    return attesterBitfield;
  }

  public long getJustifiedSlot() {
    return justifiedSlot;
  }

  public byte[] getJustifiedBlockHash() {
    return justifiedBlockHash;
  }

  public Sign.Signature getAggregateSig() {
    return aggregateSig;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AttestationRecord that = (AttestationRecord) o;
    return slot == that.slot
        && shardId == that.shardId
        && justifiedSlot == that.justifiedSlot
        && Arrays.deepEquals(obliqueParentHashes.toArray(), that.obliqueParentHashes.toArray())
        && Arrays.equals(shardBlockHash, that.shardBlockHash)
        && Objects.equals(attesterBitfield, that.attesterBitfield)
        && Arrays.equals(justifiedBlockHash, that.justifiedBlockHash)
        && Objects.equals(aggregateSig, that.aggregateSig);
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder()
            .append("AttestationRecord{")
            .append("slot=")
            .append(slot)
            .append(", shardId=")
            .append(shardId)
            .append(", obliqueParentHashes=[")
            .append(obliqueParentHashes.size())
            .append(" item(s)]")
            .append(", shardBlockHash=")
            .append(BytesValue.wrap(shardBlockHash))
            .append(", attesterBitfield=")
            .append(attesterBitfield)
            .append(", justifiedSlot=")
            .append(justifiedSlot)
            .append(", justifiedBlockHash=")
            .append(BytesValue.wrap(justifiedBlockHash))
            .append(", aggregateSig=[")
            .append(aggregateSig)
            .append("}");

    return builder.toString();
  }
}
