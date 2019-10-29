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

package org.ethereum.beacon.core;

import com.google.common.base.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Hashable;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Beacon block header structure.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#beaconblockheader">BeaconBlockHeader</a>
 *     in the spec.
 */
@SSZSerializable
public class BeaconBlockHeader implements Hashable<Hash32> {

  public static final BeaconBlockHeader EMPTY =
      new BeaconBlockHeader(
          SlotNumber.ZERO, Hash32.ZERO, Hash32.ZERO, Hash32.ZERO, BLSSignature.ZERO);

  @SSZ private final SlotNumber slot;
  @SSZ private final Hash32 parentRoot;
  @SSZ private final Hash32 stateRoot;
  @SSZ private final Hash32 bodyRoot;
  @SSZ private final BLSSignature signature;

  private Hash32 hashCache = null;

  public BeaconBlockHeader(
      SlotNumber slot,
      Hash32 parentRoot,
      Hash32 stateRoot,
      Hash32 bodyRoot,
      BLSSignature signature) {
    this.slot = slot;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.bodyRoot = bodyRoot;
    this.signature = signature;
  }

  public SlotNumber getSlot() {
    return slot;
  }

  public Hash32 getParentRoot() {
    return parentRoot;
  }

  public Hash32 getStateRoot() {
    return stateRoot;
  }

  public Hash32 getBodyRoot() {
    return bodyRoot;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public BeaconBlockHeader withStateRoot(Hash32 stateRoot) {
    return new BeaconBlockHeader(slot, parentRoot, stateRoot, bodyRoot, signature);
  }

  @Override
  public Optional<Hash32> getHash() {
    return Optional.ofNullable(hashCache);
  }

  @Override
  public void setHash(Hash32 hash) {
    this.hashCache = hash;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    BeaconBlockHeader that = (BeaconBlockHeader) object;
    return Objects.equal(slot, that.slot)
        && Objects.equal(parentRoot, that.parentRoot)
        && Objects.equal(stateRoot, that.stateRoot)
        && Objects.equal(bodyRoot, that.bodyRoot)
        && Objects.equal(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(slot, parentRoot, stateRoot, bodyRoot, signature);
  }

  @Override
  public String toString() {
    return toString(null, null);
  }

  public String toStringFull(
      @Nullable SpecConstants constants,
      @Nullable Function<? super Hashable<Hash32>, Hash32> hasher) {
    return "BlockHeader[" + toStringPriv(constants, hasher) + "]:\n";
  }

  public String toString(
      @Nullable SpecConstants constants,
      @Nullable Function<? super BeaconBlockHeader, Hash32> hasher) {
    return (hasher == null ? "?" : hasher.apply(this).toStringShort())
        + " <~ "
        + parentRoot.toStringShort();
  }

  private String toStringPriv(
      @Nullable SpecConstants constants,
      @Nullable Function<? super Hashable<Hash32>, Hash32> hasher) {
    return (hasher == null ? "?" : hasher.apply(this).toStringShort())
        + " <~ "
        + parentRoot.toStringShort()
        + ", @slot "
        + slot.toStringNumber(constants)
        + ", state="
        + stateRoot.toStringShort()
        + ", body="
        + bodyRoot.toStringShort()
        + ", sig="
        + signature;
  }
}
