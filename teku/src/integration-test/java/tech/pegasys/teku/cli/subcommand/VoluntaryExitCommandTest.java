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
import java.io.File;
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

  private final String keyManagerPubKey1 =
      "0x8b0f19f3306930d8a1e85a8084ef2caea044066dedfd3de0c22f28473dd07606da5d205ae09ee20072dc9f9e4fd32d79";
  private final String keyManagerPubKey2 =
      "0x82c2a92c6823d43bf11215eddd0f706f168945b298bf718288cf78fcef81df19cd1e5c9c49e7b8d689722fb409f0c0a4";

  private final String validatorPubKey1 =
      "0xa756543ed1f0bac08480d67b1d5ae30f808db65de40bd2006f41a727e4f4200d732cdbee4c81aa930e2ffdf561f0dd25";

  private final String validatorPubKey2 =
      "0xaa516161fd3d6857ac45a89b7f8f9ba604a8c87642e5ef9ccdab46d7dc4e32518a17a371673758073cb81c0d536cbff1";

  private final String nonExistingKey = BLSTestUtil.randomPublicKey(17).toString();

  private final String EXIT_SUBMITTED = "Exit for validator %s submitted.";

  @BeforeEach
  public void setup(ClientAndServer server) throws IOException {
    this.mockBeaconServer = server;
    configureSuccessfulSpecResponse(mockBeaconServer);
    configureSuccessfulHeadResponse(mockBeaconServer);
    configureSuccessfulGenesisResponse(mockBeaconServer);
    configureSuccessfulValidatorResponses(mockBeaconServer);
    configureSuccessfulVoluntaryExitResponse(mockBeaconServer);
    System.setOut(new PrintStream(stdOut));
  }

  @AfterEach
  public void tearDown() {
    mockBeaconServer.reset();
    System.setOut(System.out);
  }

  @Test
  public void shouldExitAllLoadedValidators() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "voluntary-exit",
          "--beacon-node-api-endpoint",
          getMockBeaconServerEndpoint(mockBeaconServer),
          "--confirmation-enabled",
          "false",
          "--data-validator-path",
          Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/validator")
              .getPath(),
          "--skip-keymanager-keys",
          "false",
          "--validator-keys",
          Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/keys")
                  .getPath()
              + File.pathSeparator
              + Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/passwords")
                  .getPath()
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertValidatorExited(keyManagerPubKey1);
    assertValidatorExited(keyManagerPubKey2);
    assertValidatorExited(validatorPubKey1);
    assertValidatorExited(validatorPubKey2);
  }

  @Test
  public void shouldExitValidatorWithPubKeyFromKeyManagerOnly() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "voluntary-exit",
          "--beacon-node-api-endpoint",
          getMockBeaconServerEndpoint(mockBeaconServer),
          "--confirmation-enabled",
          "false",
          "--data-validator-path",
          Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/validator")
              .getPath(),
          "--skip-keymanager-keys",
          "false",
          "--validator-keys",
          Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/keys")
                  .getPath()
              + File.pathSeparator
              + Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/passwords")
                  .getPath(),
          "--public-keys-filter",
          keyManagerPubKey2 + "," + nonExistingKey
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertValidatorNotExited(keyManagerPubKey1);
    assertValidatorExited(keyManagerPubKey2);
    assertValidatorNotExited(validatorPubKey1);
    assertValidatorNotExited(validatorPubKey2);
    assertValidatorNotExited(nonExistingKey);
  }

  @Test
  public void shouldExitValidatorWithPubKeyFromPathOnly() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "voluntary-exit",
          "--beacon-node-api-endpoint",
          getMockBeaconServerEndpoint(mockBeaconServer),
          "--confirmation-enabled",
          "false",
          "--data-validator-path",
          Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/validator")
              .getPath(),
          "--skip-keymanager-keys",
          "false",
          "--validator-keys",
          Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/keys")
                  .getPath()
              + File.pathSeparator
              + Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/passwords")
                  .getPath(),
          "--public-keys-filter",
          validatorPubKey1 + "," + nonExistingKey
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertValidatorExited(validatorPubKey1);
    assertValidatorNotExited(validatorPubKey2);
    assertValidatorNotExited(keyManagerPubKey1);
    assertValidatorNotExited(keyManagerPubKey2);
    assertValidatorNotExited(nonExistingKey);
  }

  @Test
  public void shouldSkipKeyManagerKeys() {
    final String[] argsNetworkOptOnParent =
        new String[] {
          "voluntary-exit",
          "--beacon-node-api-endpoint",
          getMockBeaconServerEndpoint(mockBeaconServer),
          "--confirmation-enabled",
          "false",
          "--data-validator-path",
          Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/validator")
              .getPath(),
          "--validator-keys",
          Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/keys")
                  .getPath()
              + File.pathSeparator
              + Resources.getResource(
                      "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-keys/passwords")
                  .getPath(),
          "--public-keys-filter",
          keyManagerPubKey2 + "," + validatorPubKey1
        };
    int parseResult = beaconNodeCommand.parse(argsNetworkOptOnParent);
    assertThat(parseResult).isEqualTo(0);
    assertValidatorExited(validatorPubKey1);
    assertValidatorNotExited(validatorPubKey2);
    assertValidatorNotExited(keyManagerPubKey1);
    assertValidatorNotExited(keyManagerPubKey2);
    assertValidatorNotExited(nonExistingKey);
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
            Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/head.json"),
            UTF_8);
    mockBeaconServer
        .when(request().withPath("/eth/v1/beacon/headers/head"))
        .respond(response().withStatusCode(200).withBody(testHead));
  }

  private void configureSuccessfulGenesisResponse(final ClientAndServer mockBeaconServer)
      throws IOException {
    final String testHead =
        Resources.toString(
            Resources.getResource("tech/pegasys/teku/cli/subcommand/voluntary-exit/genesis.json"),
            UTF_8);
    mockBeaconServer
        .when(request().withPath("/eth/v1/beacon/genesis"))
        .respond(response().withStatusCode(200).withBody(testHead));
  }

  private void configureSuccessfulValidatorResponses(final ClientAndServer mockBeaconServer)
      throws IOException {
    final String keyManagerValidator1 =
        Resources.toString(
            Resources.getResource(
                "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-responses/8b0f19f_response.json"),
            UTF_8);

    final String keyManagerValidator2 =
        Resources.toString(
            Resources.getResource(
                "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-responses/82c2a92_response.json"),
            UTF_8);

    final String validator1 =
        Resources.toString(
            Resources.getResource(
                "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-responses/a756543_response.json"),
            UTF_8);

    final String validator2 =
        Resources.toString(
            Resources.getResource(
                "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-responses/aa51616_response.json"),
            UTF_8);

    final String validators =
        Resources.toString(
            Resources.getResource(
                "tech/pegasys/teku/cli/subcommand/voluntary-exit/validator-responses/validators_response.json"),
            UTF_8);

    mockBeaconServer
        .when(
            request()
                .withPath("/eth/v1/beacon/states/head/validators")
                .withQueryStringParameters(Parameter.param("id", keyManagerPubKey1)))
        .respond(response().withStatusCode(200).withBody(keyManagerValidator1));

    mockBeaconServer
        .when(
            request()
                .withPath("/eth/v1/beacon/states/head/validators")
                .withQueryStringParameters(Parameter.param("id", keyManagerPubKey2)))
        .respond(response().withStatusCode(200).withBody(keyManagerValidator2));

    mockBeaconServer
        .when(
            request()
                .withPath("/eth/v1/beacon/states/head/validators")
                .withQueryStringParameters(Parameter.param("id", validatorPubKey1)))
        .respond(response().withStatusCode(200).withBody(validator1));

    mockBeaconServer
        .when(
            request()
                .withPath("/eth/v1/beacon/states/head/validators")
                .withQueryStringParameters(Parameter.param("id", validatorPubKey2)))
        .respond(response().withStatusCode(200).withBody(validator2));

    mockBeaconServer
        .when(
            request()
                .withPath("/eth/v1/beacon/states/head/validators")
                .withQueryStringParameters(
                    Parameter.param(
                        "id",
                        String.join(
                            ",",
                            validatorPubKey1,
                            keyManagerPubKey1,
                            keyManagerPubKey2,
                            validatorPubKey2))))
        .respond(response().withStatusCode(200).withBody(validators));
  }

  private void configureSuccessfulVoluntaryExitResponse(final ClientAndServer mockBeaconServer) {
    mockBeaconServer
        .when(request().withPath("/eth/v1/beacon/pool/voluntary_exits"))
        .respond(response().withStatusCode(200));
  }

  private String getTestSpecResponse() {
    return JsonBody.json(new ConfigProvider(testSpec).getConfig()).toString();
  }

  private String extractValidatorId(String pubKey) {
    return pubKey.substring(2, 9);
  }

  private void assertValidatorExited(String pubKey) {
    assertThat(stdOut.toString(UTF_8))
        .contains(String.format(EXIT_SUBMITTED, extractValidatorId(pubKey)));
  }

  private void assertValidatorNotExited(String pubKey) {
    assertThat(stdOut.toString(UTF_8))
        .doesNotContain(String.format(EXIT_SUBMITTED, extractValidatorId(pubKey)));
  }
}
