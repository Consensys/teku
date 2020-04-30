/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.state;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_fork_digest;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class ForkInfo {
  private final Fork fork;
  private final Bytes32 genesisValidatorsRoot;

  public ForkInfo(final Fork fork, final Bytes32 genesisValidatorsRoot) {
    this.fork = fork;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
  }

  public Fork getFork() {
    return fork;
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return genesisValidatorsRoot;
  }

  public Bytes4 getForkDigest() {
    return compute_fork_digest(fork.getCurrent_version(), genesisValidatorsRoot);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkInfo forkInfo = (ForkInfo) o;
    return Objects.equals(fork, forkInfo.fork)
        && Objects.equals(genesisValidatorsRoot, forkInfo.genesisValidatorsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fork, genesisValidatorsRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fork", fork)
        .add("genesisValidatorsRoot", genesisValidatorsRoot)
        .toString();
  }
}
