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

package tech.pegasys.teku.pow;

import static java.util.stream.Collectors.toMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;

class Eth1BlockFetcherTest {

  private static final UInt64 CACHE_DURATION = UInt64.valueOf(10_000);
  private static final UInt64 TWO = UInt64.valueOf(2);
  private static final int CURRENT_TIME = 50_000;
  private static final UInt64 BEFORE_CACHE_PERIOD =
      UInt64.valueOf(CURRENT_TIME).minus(CACHE_DURATION).minus(ONE);
  private static final UInt64 IN_CACHE_PERIOD_1 =
      UInt64.valueOf(CURRENT_TIME).minus(CACHE_DURATION).plus(ONE);
  private static final UInt64 IN_CACHE_PERIOD_2 = IN_CACHE_PERIOD_1.plus(ONE);
  private static final UInt64 IN_CACHE_PERIOD_3 = IN_CACHE_PERIOD_2.plus(ONE);

  private final Random random = new Random(2424);
  private final Eth1EventsChannel eth1EventsChannel = mock(Eth1EventsChannel.class);
  private final Eth1Provider eth1Provider = mock(Eth1Provider.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(CURRENT_TIME);

  private final Eth1BlockFetcher blockFetcher =
      new Eth1BlockFetcher(eth1EventsChannel, eth1Provider, timeProvider, CACHE_DURATION);

  @Test
  void shouldNotFetchBlocksBeforeDepositsAreInSync() {
    blockFetcher.fetch(BigInteger.ZERO, BigInteger.ONE);
    verifyNoInteractions(eth1Provider);
  }

  @Test
  void shouldBackfillEth1BlocksWhenFirstInSyncUntilBlockBeforeCachePeriodReached() {
    final Map<Integer, Block> blocks =
        withBlocks(
            block(100, timeProvider.getTimeInSeconds()),
            block(99, timeProvider.getTimeInSeconds().minus(ONE)),
            block(98, timeProvider.getTimeInSeconds().minus(CACHE_DURATION)),
            block(97, timeProvider.getTimeInSeconds().minus(CACHE_DURATION).minus(ONE)),
            block(96, timeProvider.getTimeInSeconds().minus(CACHE_DURATION).minus(TWO)));

    blockFetcher.onInSync(UInt64.valueOf(100));

    verifyBlockSent(blocks.get(100));
    verifyBlockSent(blocks.get(99));
    verifyBlockSent(blocks.get(98));
    verifyNoMoreBlocksSent();
    // Requested block 97 but never sent it because it was outside the cache period
    verify(eth1Provider).getGuaranteedEth1Block(UInt64.valueOf(97));
    // And never requested block 96 because it must be outside the cache period.
    verifyBlockNotRequested(96);
  }

  @Test
  void shouldNotBackfillMultipleTimes() {
    final UInt64 blockNumber = UInt64.valueOf(100);
    // First block is outside the cache range so it's the only thing we should request.
    withBlocks(
        block(
            blockNumber.intValue(),
            timeProvider.getTimeInSeconds().minus(CACHE_DURATION).minus(ONE)));

    blockFetcher.onInSync(blockNumber);
    verify(eth1Provider).getGuaranteedEth1Block(blockNumber);

    blockFetcher.onInSync(blockNumber);
    // Still only requested this block the one time.
    verify(eth1Provider).getGuaranteedEth1Block(blockNumber);
    verifyNoMoreInteractions(eth1Provider);
  }

  @Test
  void shouldStopBackfillWhenGenesisReached() {
    final Map<Integer, Block> blocks =
        withBlocks(
            block(1, timeProvider.getTimeInSeconds()),
            block(0, timeProvider.getTimeInSeconds().minus(ONE)));

    blockFetcher.onInSync(ONE);
    verifyBlockSent(blocks.get(1));
    verifyBlockSent(blocks.get(0));
    verify(eth1Provider).getGuaranteedEth1Block(ONE);
    verify(eth1Provider).getGuaranteedEth1Block(ZERO);
    verifyNoMoreInteractions(eth1Provider);
  }

  @Test
  void shouldFetchBlocksFromStartToEndOfRangeInclusive() {
    final Map<Integer, Block> blocks =
        withBlocks(
            block(0, BEFORE_CACHE_PERIOD),
            block(3, IN_CACHE_PERIOD_1),
            block(4, IN_CACHE_PERIOD_2),
            block(5, IN_CACHE_PERIOD_3));

    blockFetcher.onInSync(ZERO);

    blockFetcher.fetch(BigInteger.valueOf(3), BigInteger.valueOf(5));

    verifyBlockSent(blocks.get(3));
    verifyBlockSent(blocks.get(4));
    verifyBlockSent(blocks.get(5));
    verifyNoMoreBlocksSent();
  }

  @Test
  void shouldNotFetchAnyBlocksIfStartIsAfterEnd() {
    withBlocks(block(0, BEFORE_CACHE_PERIOD));
    blockFetcher.onInSync(UInt64.valueOf(0));
    verify(eth1Provider).getGuaranteedEth1Block(ZERO);

    blockFetcher.fetch(BigInteger.valueOf(7), BigInteger.valueOf(5));

    verifyNoMoreInteractions(eth1Provider);
  }

  @Test
  void shouldFetchSingleBlock() {
    final Map<Integer, Block> blocks =
        withBlocks(
            block(0, BEFORE_CACHE_PERIOD),
            block(3, IN_CACHE_PERIOD_1),
            block(4, IN_CACHE_PERIOD_2),
            block(5, IN_CACHE_PERIOD_3));

    blockFetcher.onInSync(ZERO);

    blockFetcher.fetch(BigInteger.valueOf(4), BigInteger.valueOf(4));

    verifyBlockSent(blocks.get(4));
    verifyNoMoreBlocksSent();
  }

  @Test
  void shouldNotFetchBlocksKnownToBeBeforeTheCachePeriod() {
    // Everything is before the cache period.
    withBlocks(block(0, ZERO), block(3, ONE), block(4, TWO), block(5, BEFORE_CACHE_PERIOD));

    blockFetcher.onInSync(ZERO);
    verify(eth1Provider).getGuaranteedEth1Block(ZERO);

    // Download the latest block first and since it's before the cache period, skip the rest
    blockFetcher.fetch(BigInteger.valueOf(3), BigInteger.valueOf(5));
    verify(eth1Provider).getEth1Block(UInt64.valueOf(5));
    verifyNoMoreInteractions(eth1Provider);
    verifyNoMoreBlocksSent();
  }

  @Test
  void shouldFetchBlocksLaterThanOneWhichWasBeforeTheCachePeriod() {
    final SafeFuture<Optional<Block>> block5Future = new SafeFuture<>();
    final SafeFuture<Optional<Block>> block6Future = new SafeFuture<>();
    final Block block5 = block(5, BEFORE_CACHE_PERIOD);
    final Block block6 = block(6, IN_CACHE_PERIOD_1);

    when(eth1Provider.getGuaranteedEth1Block(ZERO)).thenReturn(new SafeFuture<>());
    when(eth1Provider.getEth1Block(UInt64.valueOf(5))).thenReturn(block5Future);
    when(eth1Provider.getEth1Block(UInt64.valueOf(6))).thenReturn(block6Future);

    blockFetcher.onInSync(ZERO);
    verify(eth1Provider).getGuaranteedEth1Block(ZERO);

    // Fetch blocks 3-5 which will all be before cache period.
    blockFetcher.fetch(BigInteger.valueOf(3), BigInteger.valueOf(5));
    verify(eth1Provider).getEth1Block(UInt64.valueOf(5));

    // Before that completes, also ask to fetch block 6 which is in the cache period.
    blockFetcher.fetch(BigInteger.valueOf(6), BigInteger.valueOf(6));
    verifyNoMoreInteractions(eth1Provider);

    // When block 5 complete, it should request block 6
    block5Future.complete(Optional.of(block5));
    verify(eth1Provider).getEth1Block(UInt64.valueOf(6));
    verifyNoMoreInteractions(eth1Provider);

    // But we shouldn't go back to get blocks 3 & 4 because we know they're too early
    block6Future.complete(Optional.of(block6));
    verifyNoMoreInteractions(eth1Provider);
    verifyBlockSent(block6);
    verifyNoMoreBlocksSent();
  }

  private void verifyBlockNotRequested(final int blockNumber) {
    verify(eth1Provider, never()).getEth1Block(UInt64.valueOf(blockNumber));
    verify(eth1Provider, never()).getGuaranteedEth1Block(UInt64.valueOf(blockNumber));
  }

  private void verifyNoMoreBlocksSent() {
    verifyNoMoreInteractions(eth1EventsChannel);
  }

  private void verifyBlockSent(final Block block) {
    verify(eth1EventsChannel)
        .onEth1Block(Bytes32.fromHexString(block.getHash()), UInt64.valueOf(block.getTimestamp()));
  }

  private Map<Integer, Block> withBlocks(final Block... blocks) {
    for (Block block : blocks) {
      final UInt64 blockNumber = UInt64.valueOf(block.getNumber());
      when(eth1Provider.getEth1Block(blockNumber))
          .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
      when(eth1Provider.getGuaranteedEth1Block(blockNumber))
          .thenReturn(SafeFuture.completedFuture(block));
    }
    return Arrays.stream(blocks)
        .collect(toMap(block -> block.getNumber().intValueExact(), Function.identity()));
  }

  private Block block(final int blockNumber, final UInt64 blockTimestamp) {
    return block(blockNumber, blockTimestamp, Bytes32.random(random));
  }

  private Block block(final int blockNumber, final UInt64 blockTimestamp, final Bytes32 blockHash) {
    final Block block = new Block();
    block.setNumber("0x" + Integer.toHexString(blockNumber));
    block.setTimestamp("0x" + Long.toHexString(blockTimestamp.longValue()));
    block.setHash(blockHash.toHexString());
    return block;
  }
}
