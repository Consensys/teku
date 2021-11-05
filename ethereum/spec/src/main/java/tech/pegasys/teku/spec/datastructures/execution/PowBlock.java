/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class PowBlock {

  private final Bytes32 blockHash;
  private final Bytes32 parentHash;
  private final UInt256 totalDifficulty;
  private final UInt256 difficulty;

  public PowBlock(
      Bytes32 blockHash, Bytes32 parentHash, UInt256 totalDifficulty, UInt256 difficulty) {
    this.blockHash = blockHash;
    this.parentHash = parentHash;
    this.totalDifficulty = totalDifficulty;
    this.difficulty = difficulty;
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  public Bytes32 getParentHash() {
    return parentHash;
  }

  public UInt256 getTotalDifficulty() {
    return totalDifficulty;
  }

  public UInt256 getDifficulty() {
    return difficulty;
  }
}
