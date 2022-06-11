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

package tech.pegasys.teku.services.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerServiceTest {

  private final EventChannels eventChannels = mock(EventChannels.class);
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
      mock(ExecutionWeb3jClientProvider.class);
  private final ExecutionLayerManager executionLayerManager = mock(ExecutionLayerManager.class);

  private final ExecutionLayerService underTest =
      new ExecutionLayerService(eventChannels, engineWeb3jClientProvider, executionLayerManager);

  @Test
  public void addsSubscribersToTheEventChannelOnServiceStart() {
    when(eventChannels.subscribe(any(), any())).thenReturn(eventChannels);

    underTest.start().ifExceptionGetsHereRaiseABug();

    verify(eventChannels).subscribe(SlotEventsChannel.class, executionLayerManager);
    verify(eventChannels).subscribe(ExecutionLayerChannel.class, executionLayerManager);

    underTest.stop().ifExceptionGetsHereRaiseABug();
  }

  @Test
  public void engineClientProviderIsEmptyIfStub() {
    when(engineWeb3jClientProvider.isStub()).thenReturn(true);
    assertThat(underTest.getEngineWeb3jClientProvider()).isEmpty();
  }

  @Test
  public void engineClientProviderIsPresentIfNotStub() {
    when(engineWeb3jClientProvider.isStub()).thenReturn(false);
    assertThat(underTest.getEngineWeb3jClientProvider()).hasValue(engineWeb3jClientProvider);
  }
}
