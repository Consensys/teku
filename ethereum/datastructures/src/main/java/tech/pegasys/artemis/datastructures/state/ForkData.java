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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.ssz.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class ForkData {
  private final Bytes4 currentVersion;
  private final Bytes32 genesisValidatorsRoot;

  public ForkData(final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    this.currentVersion = currentVersion;
    this.genesisValidatorsRoot = genesisValidatorsRoot;
  }

  public Bytes4 getCurrentVersion() {
    return currentVersion;
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return genesisValidatorsRoot;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        List.of(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, currentVersion.getWrappedBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, genesisValidatorsRoot)));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkData forkData = (ForkData) o;
    return Objects.equals(currentVersion, forkData.currentVersion)
        && Objects.equals(genesisValidatorsRoot, forkData.genesisValidatorsRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currentVersion, genesisValidatorsRoot);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentVersion", currentVersion)
        .add("genesisValidatorsRoot", genesisValidatorsRoot)
        .toString();
  }
}
