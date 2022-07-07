/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.coordinator;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;

// TODO: rename
public class Eth1DataEx {

  private final Eth1Data eth1Data;
  private final UInt64 blockNumber;

  public Eth1DataEx(final Eth1Data eth1Data, final UInt64 blockNumber) {
    this.eth1Data = eth1Data;
    this.blockNumber = blockNumber;
  }

  public Eth1Data getEth1Data() {
    return eth1Data;
  }

  public UInt64 getBlockNumber() {
    return blockNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Eth1DataEx that = (Eth1DataEx) o;
    return Objects.equals(eth1Data, that.eth1Data) && Objects.equals(blockNumber, that.blockNumber);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eth1Data, blockNumber);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("eth1Data", eth1Data)
        .add("blockNumber", blockNumber)
        .toString();
  }
}
