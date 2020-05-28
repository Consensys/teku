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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.util.async.SafeFuture;

class ThrottlingEth1ProviderTest {

  private static final UnsignedLong ONE = UnsignedLong.valueOf(1);
  private static final UnsignedLong TWO = UnsignedLong.valueOf(2);
  private static final UnsignedLong THREE = UnsignedLong.valueOf(3);
  private static final UnsignedLong FOUR = UnsignedLong.valueOf(4);

  private final Block block1 = mock(Block.class);
  private final Block block2 = mock(Block.class);
  private final Eth1Provider delegateProvider = mock(Eth1Provider.class);
  private final ThrottlingEth1Provider provider = new ThrottlingEth1Provider(delegateProvider, 3);
  private final List<SafeFuture<Block>> blockRequests = new ArrayList<>();

  private final Answer<Object> returnBlockFuture =
      call -> {
        final SafeFuture<Block> future = new SafeFuture<>();
        blockRequests.add(future);
        return future;
      };

  @BeforeEach
  void setUp() {

    when(delegateProvider.getEth1Block(any(UnsignedLong.class))).thenAnswer(returnBlockFuture);
    when(delegateProvider.getEth1Block(any(String.class))).thenAnswer(returnBlockFuture);
    when(delegateProvider.getLatestEth1Block()).thenAnswer(returnBlockFuture);
  }

  @Test
  void shouldLimitNumberOfInFlightRequests() {
    provider.getEth1Block(ONE).reportExceptions();
    provider.getEth1Block(TWO).reportExceptions();
    provider.getEth1Block(THREE).reportExceptions();
    provider.getEth1Block(FOUR).reportExceptions();

    verify(delegateProvider).getEth1Block(ONE);
    verify(delegateProvider).getEth1Block(TWO);
    verify(delegateProvider).getEth1Block(THREE);
    verifyNoMoreInteractions(delegateProvider);
  }

  @Test
  void shouldProcessNextRequestWhenInFlightOneCompletes() {
    final SafeFuture<Block> request1 = provider.getEth1Block(ONE);
    final SafeFuture<Block> request2 = provider.getEth1Block(TWO);
    final SafeFuture<Block> request3 = provider.getEth1Block(THREE);
    final SafeFuture<Block> request4 = provider.getEth1Block(FOUR);

    verify(delegateProvider).getEth1Block(ONE);
    verify(delegateProvider).getEth1Block(TWO);
    verify(delegateProvider).getEth1Block(THREE);
    verifyNoMoreInteractions(delegateProvider);

    blockRequests.get(1).complete(block2);
    assertThat(request1).isNotDone();
    assertThat(request2).isCompletedWithValue(block2);
    assertThat(request3).isNotDone();
    assertThat(request4).isNotDone();

    verify(delegateProvider).getEth1Block(FOUR);

    blockRequests.get(0).complete(block1);
    assertThat(request1).isCompletedWithValue(block1);
    assertThat(request2).isCompletedWithValue(block2);
    assertThat(request3).isNotDone();
    assertThat(request4).isNotDone();

    // No more requests to run.
    verifyNoMoreInteractions(delegateProvider);
  }

  @Test
  void shouldThrottleTotalRequestsRegardlessOfType() {
    provider.getEth1Block(ONE).reportExceptions();
    provider.getEth1Block("TWO").reportExceptions();
    provider.getLatestEth1Block().reportExceptions();
    provider.getEth1Block(FOUR).reportExceptions();

    verify(delegateProvider).getEth1Block(ONE);
    verify(delegateProvider).getEth1Block("TWO");
    verify(delegateProvider).getLatestEth1Block();
    verifyNoMoreInteractions(delegateProvider);
  }
}
