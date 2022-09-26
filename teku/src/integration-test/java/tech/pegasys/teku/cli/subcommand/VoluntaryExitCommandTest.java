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

package tech.pegasys.teku.cli.subcommand;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.google.common.io.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.JsonBody;
import org.mockserver.model.Parameter;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;

@ExtendWith(MockServerExtension.class)
public class VoluntaryExitCommandTest {

  private final StringWriter stringWriter = new StringWriter();
  private final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  private final PrintWriter errorWriter = new PrintWriter(stringWriter, true);
  private final LoggingConfigurator loggingConfigurator = mock(LoggingConfigurator.class);

  private final BeaconNodeCommand.StartAction startAction =
      mock(BeaconNodeCommand.StartAction.class);

  private final Spec testSpec =
      SpecFactory.create(
          Resources.getResource("tech/pegasys/teku/cli/subcommand/test-spec.yaml").getPath());

  private ClientAndServer mockBeaconServer;

  private final BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(
          outputWriter, errorWriter, Collections.emptyMap(), startAction, loggingConfigurator);

  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

  private final String pubKey1 =
      "0x82c2a92c6823d43bf11215eddd0f706f168945b298bf718288cf78fcef81df19cd1e5c9c49e7b8d689722fb409f0c0a4";

  @BeforeEach
  public void setup(ClientAndServer server) throws IOException {
    this.mockBeaconServer = server;
    configureSuccessfulSpecResponse(mockBeaconServer);
    configureSuccessfulHeadResponse(mockBeaconServer);
    configureSuccessfulGenesisResponse(mockBeaconServer);
    configureSuccessfulValidatorResponse(mockBeaconServer);
    configureSuccessfulVoluntaryExitResponse(mockBeaconServer);
    System.setOut(new PrintStream(stdOut));
  }

  @AfterEach
  public void tearDown() {
    mockBeaconServer.reset();
    System.setOut(System.out);
  }

  @Test
  public void shouldExitVidatorWithPubKey() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "voluntary-exit",
          "--data-validator-path",
          Resources.getResource("tech/pegasys/teku/cli/subcommand/validator").getPath(),
          "--beacon-node-api-endpoint",
          getMockBeaconServerEndpoint(mockBeaconServer),
          "--confirmation-enabled",
          "false",
          "--validator-public-keys",
          pubKey1 + "," + BLSTestUtil.randomPublicKey(17).toString()
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertThat(stdOut.toString(UTF_8)).contains("Exit for validator 82c2a92 submitted.");
  }

  private String getMockBeaconServerEndpoint(final ClientAndServer mockBeaconServer) {
    return String.format("http://127.0.0.1:%s/", mockBeaconServer.getLocalPort());
  }

  private void configureSuccessfulSpecResponse(final ClientAndServer mockBeaconServer) {
    mockBeaconServer
        .when(request().withPath("/eth/v1/config/spec"))
        .respond(response().withStatusCode(200).withBody(getTestSpecResponse()));
  }

  private void configureSuccessfulHeadResponse(final ClientAndServer mockBeaconServer)
      throws IOException {
    final String testHead =
        Resources.toString(
            Resources.getResource("tech/pegasys/teku/cli/subcommand/head.json"), UTF_8);
    mockBeaconServer
        .when(request().withPath("/eth/v1/beacon/headers/head"))
        .respond(response().withStatusCode(200).withBody(testHead));
  }

  private void configureSuccessfulGenesisResponse(final ClientAndServer mockBeaconServer)
      throws IOException {
    final String testHead =
        Resources.toString(
            Resources.getResource("tech/pegasys/teku/cli/subcommand/genesis.json"), UTF_8);
    mockBeaconServer
        .when(request().withPath("/eth/v1/beacon/genesis"))
        .respond(response().withStatusCode(200).withBody(testHead));
  }

  private void configureSuccessfulValidatorResponse(final ClientAndServer mockBeaconServer)
      throws IOException {
    final String validator =
        Resources.toString(
            Resources.getResource("tech/pegasys/teku/cli/subcommand/82c2a92_response.json"), UTF_8);
    mockBeaconServer
        .when(
            request()
                .withPath("/eth/v1/beacon/states/head/validators")
                .withQueryStringParameters(Parameter.param("id", pubKey1)))
        .respond(response().withStatusCode(200).withBody(validator));
  }

  private void configureSuccessfulVoluntaryExitResponse(final ClientAndServer mockBeaconServer) {
    mockBeaconServer
        .when(request().withPath("/eth/v1/beacon/pool/voluntary_exits"))
        .respond(response().withStatusCode(200));
  }

  private String getTestSpecResponse() {
    return JsonBody.json(new ConfigProvider(testSpec).getConfig()).toString();
  }
}
