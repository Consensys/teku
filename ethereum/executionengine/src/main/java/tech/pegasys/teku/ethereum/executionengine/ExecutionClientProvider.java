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

package tech.pegasys.teku.ethereum.executionengine;

import static tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel.STUB_ENDPOINT_IDENTIFIER;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.web3j.protocol.Web3j;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public interface ExecutionClientProvider {
  ExecutionClientProvider STUB =
      new ExecutionClientProvider() {
        @Override
        public Web3JClient getWeb3JClient() {
          throw new InvalidConfigurationException("Stub provider");
        }

        @Override
        public Web3j getWeb3j() {
          throw new InvalidConfigurationException("Stub provider");
        }

        @Override
        public String getEndpoint() {
          return STUB_ENDPOINT_IDENTIFIER;
        }

        @Override
        public boolean isStub() {
          return true;
        }
      };

  static ExecutionClientProvider create(
      final String eeEndpoint,
      final TimeProvider timeProvider,
      final Optional<Duration> timeout,
      final Optional<String> jwtSecretFile,
      final Path beaconDataDirectory) {
    if (eeEndpoint.equals(STUB_ENDPOINT_IDENTIFIER)) {
      return STUB;
    } else {
      return new DefaultExecutionClientProvider(
          eeEndpoint, timeProvider, timeout, jwtSecretFile, beaconDataDirectory);
    }
  }

  Web3JClient getWeb3JClient();

  Web3j getWeb3j();

  String getEndpoint();

  default boolean isStub() {
    return false;
  }
}
