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

package tech.pegasys.teku.pow.fallback.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.pow.fallback.FallbackAwareEth1Provider;

@SuppressWarnings("FutureReturnValueIgnored")
public class PriorityEth1ProviderSelectorTest {

  @Test
  void assertThatSelectionWorks() {
    final Eth1Provider node1 = mock(Eth1Provider.class);
    final Eth1Provider node2 = mock(Eth1Provider.class);
    final Eth1Provider node3 = mock(Eth1Provider.class);
    final List<Eth1Provider> providers = Arrays.asList(node1, node2, node3);
    final Eth1ProviderSelector providerSelector = new PriorityEth1ProviderSelector(providers);
    final FallbackAwareEth1Provider fallbackAwareEth1Provider =
        new FallbackAwareEth1Provider(providerSelector);
    // check that first provider is the best candidate after initialization
    assertThat(providerSelector.bestCandidate()).isEqualTo(node1);
    // node1 is still ready, no update needed
    when(node1.getLatestEth1Block()).thenReturn(readyProvider());
    fallbackAwareEth1Provider.getLatestEth1Block();
    // check that node1 is still the best candidate
    assertThat(providerSelector.bestCandidate()).isEqualTo(node1);
    // node1 is now down
    when(node1.getLatestEth1Block()).thenReturn(koProvider());
    // and node2 is ready
    when(node2.getLatestEth1Block()).thenReturn(readyProvider());
    // then node2 should be the best candidate
    fallbackAwareEth1Provider.getLatestEth1Block();
    assertThat(providerSelector.bestCandidate()).isEqualTo(node2);
    // node2 is now down
    when(node2.getLatestEth1Block()).thenReturn(koProvider());
    // and node3 is ready
    when(node3.getLatestEth1Block()).thenReturn(readyProvider());
    // then node3 should be the best candidate
    fallbackAwareEth1Provider.getLatestEth1Block();
    assertThat(providerSelector.bestCandidate()).isEqualTo(node3);
  }

  private static SafeFuture<EthBlock.Block> readyProvider() {
    final SafeFuture<EthBlock.Block> blockSafeFuture = new SafeFuture<>();
    blockSafeFuture.complete(mock(EthBlock.Block.class));
    return blockSafeFuture;
  }

  private static SafeFuture<EthBlock.Block> koProvider() {
    final SafeFuture<EthBlock.Block> blockSafeFuture = new SafeFuture<>();
    blockSafeFuture.completeExceptionally(new RuntimeException("cannot get block"));
    return blockSafeFuture;
  }
}
