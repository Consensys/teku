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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static tech.pegasys.teku.cli.BeaconNodeCommand.StartAction;

import com.google.common.io.Resources;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.JsonBody;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;

@ExtendWith(MockServerExtension.class)
public class ValidatorClientCommandTest {

  private final StringWriter stringWriter = new StringWriter();
  private final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  private final PrintWriter errorWriter = new PrintWriter(stringWriter, true);

  private final StartAction startAction = mock(StartAction.class);

  private String[] argsNetworkOptDefault;
  private String[] argsNetworkOptAuto;
  private String[] argsNetworkOptAutoInConfig;

  private ClientAndServer mockBeaconServer;

  private final Spec testSpec =
      SpecFactory.create(
          Resources.getResource("tech/pegasys/teku/cli/subcommand/test-spec.yaml").getPath());

  private final String networkAutoConfigFile =
      Resources.getResource("tech/pegasys/teku/cli/subcommand/networkOption_auto_config.yaml")
          .getPath();

  private final BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(outputWriter, errorWriter, Collections.emptyMap(), startAction);

  @BeforeEach
  public void setup(ClientAndServer server) {
    this.mockBeaconServer = server;
    String mockBeaconServerEndpoint =
        String.format("http://127.0.0.1:%s/", mockBeaconServer.getLocalPort());
    argsNetworkOptDefault =
        new String[] {"vc", "--beacon-node-api-endpoint", mockBeaconServerEndpoint};
    argsNetworkOptAuto =
        new String[] {
          "vc", "--network", "auto", "--beacon-node-api-endpoint", mockBeaconServerEndpoint
        };
    argsNetworkOptAutoInConfig =
        new String[] {
          "vc",
          "--config-file",
          networkAutoConfigFile,
          "--beacon-node-api-endpoint",
          mockBeaconServerEndpoint
        };
  }

  @AfterEach
  public void tearDown() {
    mockBeaconServer.reset();
  }

  @Test
  public void autoDetectNetwork_ShouldRetryRequest_IfFailsToFetchFromBeaconNode() {
    configureMockServer(1);
    fetchAndVerifySpec(argsNetworkOptAuto);
  }

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromBeaconNode_IfEnabled() {
    configureMockServer(0);
    fetchAndVerifySpec(argsNetworkOptAuto);
  }

  @Test
  public void autoDetectNetwork_ShouldFetchNetworkDetailsFromBeaconNode_IfEnabledInConfigFile() {
    configureMockServer(0);
    fetchAndVerifySpec(argsNetworkOptAutoInConfig);
  }

  @Test
  public void networkOption_ShouldDefaultToAuto_IfNotSpecified() {
    configureMockServer(0);
    fetchAndVerifySpec(argsNetworkOptDefault);
  }

  private void fetchAndVerifySpec(String[] args) {
    int parseResult = beaconNodeCommand.parse(args);
    assertThat(parseResult).isEqualTo(0);

    TekuConfiguration config = getResultingTekuConfiguration();
    assertThat(config.eth2NetworkConfiguration().getSpec()).isEqualTo(testSpec);
  }

  private void configureMockServer(int fails) {
    this.mockBeaconServer
        .when(request().withPath("/eth/v1/config/spec"), Times.exactly(fails))
        .error(HttpError.error());

    this.mockBeaconServer
        .when(request().withPath("/eth/v1/config/spec"))
        .respond(response().withStatusCode(200).withBody(getTestSpecResponse()));
  }

  private String getTestSpecResponse() {
    return JsonBody.json(new ConfigProvider(testSpec).getConfig()).toString();
  }

  public TekuConfiguration getResultingTekuConfiguration() {
    final ArgumentCaptor<TekuConfiguration> configCaptor =
        ArgumentCaptor.forClass(TekuConfiguration.class);
    verify(startAction).start(configCaptor.capture(), eq(true));
    return configCaptor.getValue();
  }
}
