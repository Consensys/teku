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

package tech.pegasys.teku.storage.api.schema;

import java.math.BigInteger;
import java.util.Optional;

public class ReplayDepositsResult {
  private static final BigInteger NEGATIVE_ONE = BigInteger.valueOf(-1);
  private static final ReplayDepositsResult EMPTY =
      new ReplayDepositsResult(NEGATIVE_ONE, Optional.empty(), false);

  private final BigInteger lastProcessedBlockNumber;
  private final Optional<BigInteger> lastProcessedDepositIndex;
  private final boolean pastMinGenesisBlock;

  private ReplayDepositsResult(
      final BigInteger lastProcessedBlockNumber,
      final Optional<BigInteger> lastProcessedDepositIndex,
      final boolean pastMinGenesisBlock) {
    this.lastProcessedBlockNumber = lastProcessedBlockNumber;
    this.lastProcessedDepositIndex = lastProcessedDepositIndex;
    this.pastMinGenesisBlock = pastMinGenesisBlock;
  }

  public static ReplayDepositsResult empty() {
    return EMPTY;
  }

  public static ReplayDepositsResult create(
      final BigInteger lastProcessedBlockNumber,
      final BigInteger lastProcessedDepositIndex,
      final boolean pastMinGenesisBlock) {
    return new ReplayDepositsResult(
        lastProcessedBlockNumber, Optional.of(lastProcessedDepositIndex), pastMinGenesisBlock);
  }

  public static ReplayDepositsResult create(
      final BigInteger lastProcessedBlockNumber,
      final Optional<BigInteger> lastProcessedDepositIndex,
      final boolean pastMinGenesisBlock) {
    return new ReplayDepositsResult(
        lastProcessedBlockNumber, lastProcessedDepositIndex, pastMinGenesisBlock);
  }

  public BigInteger getLastProcessedBlockNumber() {
    return lastProcessedBlockNumber;
  }

  public Optional<BigInteger> getLastProcessedDepositIndex() {
    return lastProcessedDepositIndex;
  }

  public BigInteger getFirstUnprocessedBlockNumber() {
    return lastProcessedBlockNumber.add(BigInteger.ONE);
  }

  public boolean isPastMinGenesisBlock() {
    return pastMinGenesisBlock;
  }
}
