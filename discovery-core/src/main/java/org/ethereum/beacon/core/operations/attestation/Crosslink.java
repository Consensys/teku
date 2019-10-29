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
import java.util.function.Function;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * A Crosslink record.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#crosslink">Crosslink</a>
 *     in the spec.
 */
@SSZSerializable
public class Crosslink {

  public static final Crosslink EMPTY =
      new Crosslink(ShardNumber.ZERO, Hash32.ZERO, EpochNumber.ZERO, EpochNumber.ZERO, Hash32.ZERO);

  /** Shard number. */
  @SSZ private final ShardNumber shard;
  /** Root of the previous crosslink. */
  @SSZ private final Hash32 parentRoot;
  /** Crosslinking data from epochs [start....end-1]. */
  @SSZ private final EpochNumber startEpoch;

  @SSZ private final EpochNumber endEpoch;
  /** Root of the crosslinked shard data since the previous crosslink. */
  @SSZ private final Hash32 dataRoot;

  public Crosslink(
      ShardNumber shard,
      Hash32 parentRoot,
      EpochNumber startEpoch,
      EpochNumber endEpoch,
      Hash32 dataRoot) {
    this.shard = shard;
    this.parentRoot = parentRoot;
    this.startEpoch = startEpoch;
    this.endEpoch = endEpoch;
    this.dataRoot = dataRoot;
  }

  public ShardNumber getShard() {
    return shard;
  }

  public EpochNumber getStartEpoch() {
    return startEpoch;
  }

  public EpochNumber getEndEpoch() {
    return endEpoch;
  }

  public Hash32 getParentRoot() {
    return parentRoot;
  }

  public Hash32 getDataRoot() {
    return dataRoot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Crosslink crosslink = (Crosslink) o;
    return Objects.equal(shard, crosslink.shard)
        && Objects.equal(startEpoch, crosslink.startEpoch)
        && Objects.equal(endEpoch, crosslink.endEpoch)
        && Objects.equal(parentRoot, crosslink.parentRoot)
        && Objects.equal(dataRoot, crosslink.dataRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(shard, startEpoch, endEpoch, parentRoot, dataRoot);
  }

  public String toStringShort(Function<Object, Hash32> hasher) {
    return String.format(
        "%s: {[%s..%s], %s <~ %s}",
        shard,
        startEpoch,
        endEpoch,
        parentRoot.toStringShort(),
        hasher.apply(this).toStringShort());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("shard", shard)
        .add("startEpoch", startEpoch)
        .add("endEpoch", endEpoch)
        .add("parentRoot", parentRoot.toStringShort())
        .add("dataRoot", dataRoot.toStringShort())
        .toString();
  }
}
