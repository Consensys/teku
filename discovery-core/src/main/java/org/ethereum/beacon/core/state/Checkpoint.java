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

import com.google.common.base.Objects;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

/**
 * Checkpoint type.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.0/specs/core/0_beacon-chain.md#checkpoint">Checkpoint</a>
 *     in the spec.
 */
@SSZSerializable
public class Checkpoint {

  public static final Checkpoint EMPTY = new Checkpoint(EpochNumber.ZERO, Hash32.ZERO);

  @SSZ private final EpochNumber epoch;
  @SSZ private final Hash32 root;

  public Checkpoint(EpochNumber epoch, Hash32 root) {
    this.epoch = epoch;
    this.root = root;
  }

  public EpochNumber getEpoch() {
    return epoch;
  }

  public Hash32 getRoot() {
    return root;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Checkpoint that = (Checkpoint) o;
    return Objects.equal(epoch, that.epoch) && Objects.equal(root, that.root);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(epoch, root);
  }
}
