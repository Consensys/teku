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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

public class BeaconBlocksByRootRequestMessage implements RpcRequest, SSZContainer {

  private final SSZMutableList<Bytes32> blockRoots =
      SSZList.createMutable(Bytes32.class, MAX_REQUEST_BLOCKS);

  public BeaconBlocksByRootRequestMessage(final List<Bytes32> blockRoots) {
    this.blockRoots.addAll(blockRoots);
  }

  public SSZList<Bytes32> getBlockRoots() {
    return blockRoots;
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockRoots);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlocksByRootRequestMessage)) {
      return false;
    }

    BeaconBlocksByRootRequestMessage other = (BeaconBlocksByRootRequestMessage) obj;
    return Objects.equals(this.blockRoots, other.blockRoots);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("blockRoots", blockRoots).toString();
  }

  @Override
  public int getMaximumRequestChunks() {
    return blockRoots.size();
  }
}
