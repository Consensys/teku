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

package org.ethereum.beacon.core.state;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.uint.UInt64;

/**
 * Keeps eth1 data.
 *
 * @see BeaconState
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#eth1data">Eth1Data</a>
 *     in the spec.
 */
@SSZSerializable
public class Eth1Data {
  public static final Eth1Data EMPTY = new Eth1Data(Hash32.ZERO, UInt64.ZERO, Hash32.ZERO);

  /** Root of the deposit tree. */
  @SSZ private final Hash32 depositRoot;
  /** Total number of deposits. */
  @SSZ private final UInt64 depositCount;
  /** Hash of eth1 block which {@code depositRoot} relates to. */
  @SSZ private final Hash32 blockHash;

  public Eth1Data(Hash32 depositRoot, UInt64 depositCount, Hash32 blockHash) {
    this.depositRoot = depositRoot;
    this.depositCount = depositCount;
    this.blockHash = blockHash;
  }

  public Hash32 getDepositRoot() {
    return depositRoot;
  }

  public UInt64 getDepositCount() {
    return depositCount;
  }

  public Hash32 getBlockHash() {
    return blockHash;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Eth1Data eth1Data = (Eth1Data) o;
    return Objects.equal(depositRoot, eth1Data.depositRoot)
        && Objects.equal(depositCount, eth1Data.depositCount)
        && Objects.equal(blockHash, eth1Data.blockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(depositRoot, depositCount, blockHash);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("depositRoot", depositRoot.toStringShort())
        .add("depositCount", depositCount)
        .add("block", blockHash.toStringShort())
        .toString();
  }
}
