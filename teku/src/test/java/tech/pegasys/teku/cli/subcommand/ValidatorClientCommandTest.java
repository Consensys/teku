/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.google.common.io.Resources;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;

public class ValidatorClientCommandTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromBeaconNodeIfEnabled() {
    final String endpoint = "http://localhost:5004";
    final Spec testSpec =
        SpecFactory.create(
            Resources.getResource("tech/pegasys/teku/cli/subcommand/test-spec.yaml").getPath());
    final String[] args = {"vc", "--network", "auto", "--beacon-node-api-endpoint", endpoint};

    try (MockedStatic<RemoteSpecLoader> mockValidatorClient = mockStatic(RemoteSpecLoader.class)) {
      mockValidatorClient
          .when(() -> RemoteSpecLoader.getSpec(URI.create(endpoint)))
          .thenReturn(testSpec);

      int parseResult = beaconNodeCommand.parse(args);
      assertThat(parseResult).isEqualTo(0);
      TekuConfiguration config = getResultingTekuConfiguration(true);
      assertThat(config.eth2NetworkConfiguration().getSpec()).isEqualTo(testSpec);
    }
  }
}
