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

package org.ethereum.beacon.core.operations.attestation;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Attestation data that validators are signing off on.
 *
 * @see Attestation
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#attestationdata">AttestationData
 *     in the spec</a>
 */
@SSZSerializable
public class AttestationData {

  // LMD GHOST vote:

  /** Root of the signed beacon block. */
  @SSZ private final Hash32 beaconBlockRoot;

  // FFG vote:

  @SSZ private final Checkpoint source;
  @SSZ private final Checkpoint target;

  // Crosslink vote:

  @SSZ private final Crosslink crosslink;

  public AttestationData(
      Hash32 beaconBlockRoot, Checkpoint source, Checkpoint target, Crosslink crosslink) {
    this.beaconBlockRoot = beaconBlockRoot;
    this.source = source;
    this.target = target;
    this.crosslink = crosslink;
  }

  public Hash32 getBeaconBlockRoot() {
    return beaconBlockRoot;
  }

  public Checkpoint getSource() {
    return source;
  }

  public Checkpoint getTarget() {
    return target;
  }

  public Crosslink getCrosslink() {
    return crosslink;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AttestationData that = (AttestationData) o;
    return Objects.equal(beaconBlockRoot, that.beaconBlockRoot)
        && Objects.equal(source, that.source)
        && Objects.equal(target, that.target)
        && Objects.equal(crosslink, that.crosslink);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(beaconBlockRoot, source, target, crosslink);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("crosslink", crosslink)
        .add("beaconBlockRoot", beaconBlockRoot.toStringShort())
        .add("sourceEpoch", source.getEpoch())
        .add("sourceRoot", source.getRoot().toStringShort())
        .add("targetEpoch", target.getEpoch())
        .add("targetRoot", target.getRoot().toStringShort())
        .toString();
  }
}
