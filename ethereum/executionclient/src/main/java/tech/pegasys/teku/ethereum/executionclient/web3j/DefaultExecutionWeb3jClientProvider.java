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

package tech.pegasys.teku.ethereum.executionclient.web3j;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.util.Optional;
import org.web3j.protocol.Web3j;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class DefaultExecutionWeb3jClientProvider implements ExecutionWeb3jClientProvider {
  private final String eeEndpoint;
  private final Duration timeout;
  private final Optional<JwtConfig> jwtConfig;
  private final TimeProvider timeProvider;
  private final ExecutionClientEventsChannel executionClientEventsPublisher;

  private boolean alreadyBuilt = false;
  private Web3JClient web3JClient;

  DefaultExecutionWeb3jClientProvider(
      final String eeEndpoint,
      final Duration timeout,
      final Optional<JwtConfig> jwtConfig,
      final TimeProvider timeProvider,
      final ExecutionClientEventsChannel executionClientEventsPublisher) {
    checkNotNull(eeEndpoint);
    this.eeEndpoint = eeEndpoint;
    this.timeout = timeout;
    this.jwtConfig = jwtConfig;
    this.timeProvider = timeProvider;
    this.executionClientEventsPublisher = executionClientEventsPublisher;
  }

  private synchronized void buildClient() {
    if (alreadyBuilt) {
      return;
    }
    Web3jClientBuilder web3JClientBuilder = new Web3jClientBuilder();
    this.web3JClient =
        web3JClientBuilder
            .endpoint(eeEndpoint)
            .timeout(timeout)
            .jwtConfigOpt(jwtConfig)
            .timeProvider(timeProvider)
            .executionClientEventsPublisher(executionClientEventsPublisher)
            .build();
    this.alreadyBuilt = true;
  }

  @Override
  public Web3JClient getWeb3JClient() {
    buildClient();
    return web3JClient;
  }

  @Override
  public Web3j getWeb3j() {
    buildClient();
    return web3JClient.getEth1Web3j();
  }

  @Override
  public String getEndpoint() {
    return eeEndpoint;
  }
}
