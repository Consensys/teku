/*
 * Copyright 2022 ConsenSys AG.
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.web3j.protocol.Web3j;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtSecretKeyLoader;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class DefaultExecutionWeb3jClientProvider implements ExecutionWeb3jClientProvider {
  private final String eeEndpoint;
  private final TimeProvider timeProvider;
  private final Optional<String> jwtSecretFile;
  private final boolean jwtSupported;
  private final Duration timeout;
  private final Path beaconDataDirectory;
  private boolean alreadyBuilt = false;
  private Web3JClient web3JClient;

  DefaultExecutionWeb3jClientProvider(
      final String eeEndpoint,
      final TimeProvider timeProvider,
      final Duration timeout,
      final Optional<String> jwtSecretFile,
      final boolean jwtSupported,
      final Path beaconDataDirectory) {
    checkNotNull(eeEndpoint);
    this.eeEndpoint = eeEndpoint;
    this.timeProvider = timeProvider;
    this.timeout = timeout;
    this.jwtSecretFile = jwtSecretFile;
    this.beaconDataDirectory = beaconDataDirectory;
    this.jwtSupported = jwtSupported;
  }

  private synchronized void buildClient() {
    if (alreadyBuilt) {
      return;
    }
    Optional<JwtConfig> jwtConfig = Optional.empty();
    if (jwtSupported) {
      JwtSecretKeyLoader keyLoader = new JwtSecretKeyLoader(jwtSecretFile, beaconDataDirectory);
      jwtConfig = Optional.of(new JwtConfig(keyLoader.getSecretKey()));
    }
    Web3jClientBuilder web3JClientBuilder = new Web3jClientBuilder();
    this.web3JClient =
        web3JClientBuilder
            .endpoint(eeEndpoint)
            .timeout(timeout)
            .jwtConfigOpt(jwtConfig)
            .timeProvider(timeProvider)
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
