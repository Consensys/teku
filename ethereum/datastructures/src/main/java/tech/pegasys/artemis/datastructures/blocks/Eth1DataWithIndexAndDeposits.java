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

package tech.pegasys.artemis.datastructures.blocks;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;

public class Eth1DataWithIndexAndDeposits extends Eth1Data
    implements Comparable<Eth1DataWithIndexAndDeposits> {
  UnsignedLong blockNumber;
  List<DepositWithIndex> deposits;

  public Eth1DataWithIndexAndDeposits(
      UnsignedLong blockNumber, List<DepositWithIndex> deposits, Bytes32 block_hash) {
    this.blockNumber = blockNumber;
    this.deposits = deposits;
    this.setBlock_hash(block_hash);
  }

  public UnsignedLong getBlockNumber() {
    return blockNumber;
  }

  public void setBlockNumber(UnsignedLong blockNumber) {
    this.blockNumber = blockNumber;
  }

  public List<DepositWithIndex> getDeposits() {
    return deposits;
  }

  public void setDeposits(List<DepositWithIndex> deposits) {
    this.deposits = deposits;
  }

  @Override
  public int compareTo(@NotNull Eth1DataWithIndexAndDeposits o) {
    return this.getBlockNumber().compareTo(o.getBlockNumber());
  }
}
