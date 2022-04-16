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

package tech.pegasys.teku.services.executionengine;

import java.nio.file.Path;
import java.util.Optional;
import org.web3j.protocol.Web3j;
import tech.pegasys.teku.ethereum.executionlayer.client.Web3JClient;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;

public interface ExecutionClientProvider {
  String STUB_ENDPOINT_IDENTIFIER = "stub";
  ExecutionClientProvider NOOP =
      new ExecutionClientProvider() {
        @Override
        public Optional<Web3JClient> getWeb3JClient() {
          return Optional.empty();
        }

        @Override
        public Optional<Web3j> getWeb3j() {
          return Optional.empty();
        }

        @Override
        public Optional<String> getEndpoint() {
          return Optional.empty();
        }
      };
  ExecutionClientProvider STUB =
      new ExecutionClientProvider() {
        @Override
        public Optional<Web3JClient> getWeb3JClient() {
          return Optional.empty();
        }

        @Override
        public Optional<Web3j> getWeb3j() {
          return Optional.empty();
        }

        @Override
        public Optional<String> getEndpoint() {
          return Optional.of(STUB_ENDPOINT_IDENTIFIER);
        }

        @Override
        public boolean isStub() {
          return true;
        }
      };

  static ExecutionClientProvider create(
      final ServiceConfig serviceConfig, final ExecutionEngineConfiguration engineConfiguration) {
    return create(
        engineConfiguration.getEndpoint(),
        serviceConfig.getTimeProvider(),
        engineConfiguration.getJwtSecretFile(),
        serviceConfig.getDataDirLayout().getBeaconDataDirectory());
  }

  static ExecutionClientProvider create(
      final String eeEndpoint,
      final TimeProvider timeProvider,
      final Optional<String> jwtSecretFile,
      final Path beaconDataDirectory) {
    if (eeEndpoint.equals(STUB_ENDPOINT_IDENTIFIER)) {
      return STUB;
    } else {
      return new TrustedExecutionClientProvider(
          eeEndpoint, timeProvider, jwtSecretFile, beaconDataDirectory);
    }
  }

  Optional<Web3JClient> getWeb3JClient();

  Optional<Web3j> getWeb3j();

  Optional<String> getEndpoint();

  default boolean isStub() {
    return false;
  }
}
