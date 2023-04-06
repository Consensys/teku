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

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.teku.ethereum.executionclient.OkHttpClientCreator;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

class Web3jHttpClient extends Web3JClient {
  private static final Logger LOG = LogManager.getLogger();

  Web3jHttpClient(
      final EventLogger eventLog,
      final URI endpoint,
      final TimeProvider timeProvider,
      final Duration timeout,
      final Optional<JwtConfig> jwtConfig,
      final ExecutionClientEventsChannel executionClientEventsPublisher,
      final Collection<String> nonCriticalMethods) {
    super(eventLog, timeProvider, executionClientEventsPublisher, nonCriticalMethods);
    final OkHttpClient okHttpClient =
        OkHttpClientCreator.create(timeout, LOG, jwtConfig, timeProvider);
    final Web3jService httpService = new HttpService(endpoint.toString(), okHttpClient);
    initWeb3jService(httpService);
  }
}
