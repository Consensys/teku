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
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.Deposit;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Hashable;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Beacon chain block.
 *
 * <p>It consists of a header fields and {@link BeaconBlockBody}.
 *
 * @see BeaconBlockBody
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#beaconblock">BeaconBlock
 *     in the spec</a>
 */
@SSZSerializable
public class BeaconBlock implements Hashable<Hash32> {

  /** Number of a slot that block does belong to. */
  @SSZ private final SlotNumber slot;
  /** A hash of parent block. */
  @SSZ private final Hash32 parentRoot;
  /** A hash of the state that is created by applying a block to the previous state. */
  @SSZ private final Hash32 stateRoot;
  /** Block body. */
  @SSZ private final BeaconBlockBody body;
  /** Proposer's signature. */
  @SSZ private final BLSSignature signature;

  private Hash32 hashCache = null;

  public BeaconBlock(
      SlotNumber slot,
      Hash32 parentRoot,
      Hash32 stateRoot,
      BeaconBlockBody body,
      BLSSignature signature) {
    this.slot = slot;
    this.parentRoot = parentRoot;
    this.stateRoot = stateRoot;
    this.body = body;
    this.signature = signature;
  }

  public BeaconBlock(BeaconBlockHeader header, BeaconBlockBody body) {
    this(
        header.getSlot(),
        header.getParentRoot(),
        header.getStateRoot(),
        body,
        header.getSignature());
  }

  @Override
  public Optional<Hash32> getHash() {
    return Optional.ofNullable(hashCache);
  }

  @Override
  public void setHash(Hash32 hash) {
    this.hashCache = hash;
  }

  public BeaconBlock withStateRoot(Hash32 stateRoot) {
    return new BeaconBlock(slot, parentRoot, stateRoot, body, signature);
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

  public BLSSignature getSignature() {
    return signature;
  }

  public BeaconBlockBody getBody() {
    return body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BeaconBlock block = (BeaconBlock) o;
    return Objects.equal(slot, block.slot)
        && Objects.equal(parentRoot, block.parentRoot)
        && Objects.equal(stateRoot, block.stateRoot)
        && Objects.equal(signature, block.signature)
        && Objects.equal(body, block.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(slot, parentRoot, stateRoot, signature, body);
  }

  @Override
  public String toString() {
    return toString(null, null, null);
  }

  public String toStringFull(
      @Nullable SpecConstants constants,
      @Nullable Time beaconStart,
      @Nullable Function<? super Hashable<Hash32>, Hash32> hasher) {
    StringBuilder ret =
        new StringBuilder("Block[" + toStringPriv(constants, beaconStart, hasher) + "]:\n");
    for (Attestation attestation : body.getAttestations()) {
      ret.append("  " + attestation.toString(constants, beaconStart) + "\n");
    }
    for (Deposit deposit : body.getDeposits()) {
      ret.append("  " + deposit.toString() + "\n");
    }
    for (VoluntaryExit voluntaryExit : body.getVoluntaryExits()) {
      ret.append("  " + voluntaryExit.toString(constants) + "\n");
    }
    for (ProposerSlashing proposerSlashing : body.getProposerSlashings()) {
      ret.append("  " + proposerSlashing.toString(constants, hasher) + "\n");
    }

    for (AttesterSlashing attesterSlashing : body.getAttesterSlashings()) {
      ret.append("  " + attesterSlashing.toString() + "\n");
    }

    return ret.toString();
  }

  public String toString(
      @Nullable SpecConstants constants,
      @Nullable Time beaconStart,
      @Nullable Function<? super Hashable<Hash32>, Hash32> hasher) {
    String ret = "Block[" + toStringPriv(constants, beaconStart, hasher);
    if (!body.getAttestations().isEmpty()) {
      ret +=
          ", atts: ["
              + body.getAttestations().stream()
                  .map(a -> a.toStringShort(constants))
                  .collect(Collectors.joining(", "))
              + "]";
    }
    if (!body.getDeposits().isEmpty()) {
      ret +=
          ", depos: ["
              + body.getDeposits().stream().map(Deposit::toString).collect(Collectors.joining(", "))
              + "]";
    }
    if (!body.getVoluntaryExits().isEmpty()) {
      ret +=
          ", exits: ["
              + body.getVoluntaryExits().stream()
                  .map(a -> a.toString(constants))
                  .collect(Collectors.joining(", "))
              + "]";
    }
    if (!body.getAttesterSlashings().isEmpty()) {
      ret +=
          ", attSlash: ["
              + body.getAttesterSlashings().stream()
                  .map(AttesterSlashing::toString)
                  .collect(Collectors.joining(", "))
              + "]";
    }
    if (!body.getProposerSlashings().isEmpty()) {
      ret +=
          ", propSlash: ["
              + body.getProposerSlashings().stream()
                  .map(a -> a.toString(constants, hasher))
                  .collect(Collectors.joining(", "))
              + "]";
    }
    ret += "]";

    return ret;
  }

  private String toStringPriv(
      @Nullable SpecConstants constants,
      @Nullable Time beaconStart,
      @Nullable Function<? super Hashable<Hash32>, Hash32> hasher) {
    return (hasher == null ? "?" : hasher.apply(this).toStringShort())
        + " <~ "
        + parentRoot.toStringShort()
        + ", @slot "
        + slot.toStringNumber(constants)
        + ", state="
        + stateRoot.toStringShort()
        + ", randao="
        + body.getRandaoReveal().toString()
        + ", "
        + body.getEth1Data()
        + ", sig="
        + signature;
  }

  public static class Builder {
    private SlotNumber slot;
    private Hash32 parentRoot;
    private Hash32 stateRoot;
    private BLSSignature signature;
    private BeaconBlockBody body;

    private Builder() {}

    public static Builder createEmpty() {
      return new Builder();
    }

    public static Builder fromBlock(BeaconBlock block) {
      Builder builder = new Builder();

      builder.slot = block.slot;
      builder.parentRoot = block.parentRoot;
      builder.stateRoot = block.stateRoot;
      builder.signature = block.signature;
      builder.body = block.body;

      return builder;
    }

    public Builder withSlot(SlotNumber slot) {
      this.slot = slot;
      return this;
    }

    public Builder withParentRoot(Hash32 parentRoot) {
      this.parentRoot = parentRoot;
      return this;
    }

    public Builder withStateRoot(Hash32 stateRoot) {
      this.stateRoot = stateRoot;
      return this;
    }

    public Builder withSignature(BLSSignature signature) {
      this.signature = signature;
      return this;
    }

    public Builder withBody(BeaconBlockBody body) {
      this.body = body;
      return this;
    }

    public BeaconBlock build() {
      assert slot != null;
      assert parentRoot != null;
      assert stateRoot != null;
      assert signature != null;
      assert body != null;

      return new BeaconBlock(slot, parentRoot, stateRoot, body, signature);
    }
  }
}
