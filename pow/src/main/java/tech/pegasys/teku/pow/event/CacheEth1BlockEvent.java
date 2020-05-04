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

package tech.pegasys.teku.pow.event;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class CacheEth1BlockEvent {

  private final UnsignedLong blockNumber;
  private final Bytes32 blockHash;
  private final UnsignedLong blockTimestamp;
  private final Bytes32 depositRoot;
  private final UnsignedLong depositCount;

  public CacheEth1BlockEvent(
      UnsignedLong blockNumber,
      Bytes32 blockHash,
      UnsignedLong blockTimestamp,
      Bytes32 depositRoot,
      UnsignedLong depositCount) {
    this.blockNumber = blockNumber;
    this.blockHash = blockHash;
    this.blockTimestamp = blockTimestamp;
    this.depositRoot = depositRoot;
    this.depositCount = depositCount;
  }

  public UnsignedLong getBlockNumber() {
    return blockNumber;
  }

  public UnsignedLong getBlockTimestamp() {
    return blockTimestamp;
  }

  public Bytes32 getDepositRoot() {
    return depositRoot;
  }

  public UnsignedLong getDepositCount() {
    return depositCount;
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockNumber", blockNumber)
        .add("blockHash", blockHash)
        .add("blockTimestamp", blockTimestamp)
        .add("depositRoot", depositRoot)
        .add("depositCount", depositCount)
        .toString();
  }
}
