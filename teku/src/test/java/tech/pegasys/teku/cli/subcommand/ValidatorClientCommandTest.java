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
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.google.common.io.Resources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.JsonBody;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;

@ExtendWith(MockServerExtension.class)
public class ValidatorClientCommandTest extends AbstractBeaconNodeCommandTest {
  private String[] args;
  final Spec testSpec =
      SpecFactory.create(
          Resources.getResource("tech/pegasys/teku/cli/subcommand/test-spec.yaml").getPath());
  private ClientAndServer clientServer;
  private String endpoint;

  @BeforeEach
  public void setup(ClientAndServer cs) {
    this.clientServer = cs;
    endpoint = String.format("http://127.0.0.1:%s/", clientServer.getLocalPort());
    args = new String[] {"vc", "--network", "auto", "--beacon-node-api-endpoint", endpoint};
  }

  @AfterEach
  public void tearDown() {
    clientServer.reset();
  }

  @Test
  public void autoDetectNetwork_ShouldDisplayErrorMsg_IfFailsToFetchFromBeaconNode() {
    beaconNodeCommand.parse(args);
    assertThat(getCommandLineOutput())
        .containsIgnoringCase(
            String.format(
                "failed to retrieve network spec from beacon node endpoint '%s'", endpoint));
  }

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromBeaconNode_IfEnabled() {
    this.clientServer
        .when(request().withPath("/eth/v1/config/spec"))
        .respond(response().withStatusCode(200).withBody(getTestSpecResponse()));

    int parseResult = beaconNodeCommand.parse(args);
    assertThat(parseResult).isEqualTo(0);
    TekuConfiguration config = getResultingTekuConfiguration(true);
    assertThat(config.eth2NetworkConfiguration().getSpec()).isEqualTo(testSpec);
  }

  private String getTestSpecResponse() {
    return JsonBody.json(new ConfigProvider(testSpec).getConfig()).toString();
  }
}
