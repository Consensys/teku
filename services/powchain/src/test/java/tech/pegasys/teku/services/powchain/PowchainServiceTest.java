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

package tech.pegasys.teku.services.powchain;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.executionclient.web3j.ExecutionWeb3jClientProvider;
import tech.pegasys.teku.ethereum.executionclient.web3j.Web3JClient;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;

public class PowchainServiceTest {
  private final ServiceConfig serviceConfig = mock(ServiceConfig.class);
  private final PowchainConfiguration powConfig = mock(PowchainConfiguration.class);
  private final ExecutionWeb3jClientProvider engineWeb3jClientProvider =
      mock(ExecutionWeb3jClientProvider.class);
  private final Web3JClient web3JClient = mock(Web3JClient.class);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  @BeforeEach
  public void setup() {
    when(powConfig.isEnabled()).thenReturn(false);
    when(powConfig.getSpec()).thenReturn(spec);
    when(engineWeb3jClientProvider.getWeb3JClient()).thenReturn(web3JClient);
  }

  @Test
  public void shouldFail_WhenNeitherEth1NorExecutionEndpoint() {
    assertThatThrownBy(() -> new PowchainService(serviceConfig, powConfig, Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldFail_WhenNoEth1EndpointButWebsocketsExecutionEndpoint() {
    when(web3JClient.isWebsocketsClient()).thenReturn(true);
    assertThatThrownBy(
            () ->
                new PowchainService(
                    serviceConfig, powConfig, Optional.of(engineWeb3jClientProvider)))
        .hasMessageContaining("not compatible with Websockets");
  }

  @Test
  public void shouldFallbackToNonWebsocketsEndpoint_WhenEth1EndpointNotProvided() {
    when(web3JClient.isWebsocketsClient()).thenReturn(false);
    when(serviceConfig.getTimeProvider()).thenReturn(mock(TimeProvider.class));
    when(serviceConfig.getMetricsSystem()).thenReturn(mock(MetricsSystem.class));
    when(powConfig.getDepositContract()).thenReturn(Eth1Address.ZERO);
    final EventChannels eventChannels = mock(EventChannels.class);
    when(eventChannels.getPublisher(Eth1EventsChannel.class))
        .thenReturn(mock(Eth1EventsChannel.class));
    when(eventChannels.getPublisher(eq(Eth1DepositStorageChannel.class), any(AsyncRunner.class)))
        .thenReturn(mock(Eth1DepositStorageChannel.class));
    when(serviceConfig.getEventChannels()).thenReturn(eventChannels);
    assertThatNoException()
        .isThrownBy(
            () ->
                new PowchainService(
                    serviceConfig, powConfig, Optional.of(engineWeb3jClientProvider)));
  }
}
