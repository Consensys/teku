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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import tech.pegasys.teku.spec.TestSpecFactory;

@ExtendWith(MockServerExtension.class)
public class VoluntaryExitCommandTest {
  private InputStream originalSystemIn;
  private PrintStream originalSystemOut;
  private PrintStream originalSytstemErr;
  private final StringWriter stringWriter = new StringWriter();
  private final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  private final PrintWriter errorWriter = new PrintWriter(stringWriter, true);

  private ClientAndServer mockBeaconServer;

  private final BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(
          outputWriter,
          errorWriter,
          Collections.emptyMap(),
          mock(BeaconNodeCommand.StartAction.class),
          mock(LoggingConfigurator.class));

  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();

  private final ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

  private final String keyManagerPubKey1 =
      "0x8b0f19f3306930d8a1e85a8084ef2caea044066dedfd3de0c22f28473dd07606da5d205ae09ee20072dc9f9e4fd32d79";
  private final String keyManagerPubKey2 =
      "0x82c2a92c6823d43bf11215eddd0f706f168945b298bf718288cf78fcef81df19cd1e5c9c49e7b8d689722fb409f0c0a4";

  private final String validatorPubKey1 =
      "0xa756543ed1f0bac08480d67b1d5ae30f808db65de40bd2006f41a727e4f4200d732cdbee4c81aa930e2ffdf561f0dd25";

  private final String validatorPubKey2 =
      "0xaa516161fd3d6857ac45a89b7f8f9ba604a8c87642e5ef9ccdab46d7dc4e32518a17a371673758073cb81c0d536cbff1";

  private final String nonExistingKey = BLSTestUtil.randomPublicKey(17).toString();

  private final String exitSubmitted = "Exit for validator %s submitted.";

  private List<String> commandArgs;

  @BeforeEach
  public void setup(ClientAndServer server) throws IOException {
    this.mockBeaconServer = server;
    configureSuccessfulHeadResponse(mockBeaconServer);
    configureSuccessfulGenesisResponse(mockBeaconServer);
    configureSuccessfulValidatorResponses(mockBeaconServer);
    configureSuccessfulVoluntaryExitResponse(mockBeaconServer);
    originalSystemIn = System.in;
    originalSystemOut = System.out;
    originalSytstemErr = System.err;
    System.setOut(new PrintStream(stdOut));
    System.setErr(new PrintStream(stdErr));

    commandArgs =
        List.of(
            "voluntary-exit",
            "--beacon-node-api-endpoint",
            getMockBeaconServerEndpoint(mockBeaconServer),
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
                    .getPath());
  }

  @AfterEach
  public void tearDown() {
    mockBeaconServer.reset();
    System.setOut(originalSystemOut);
    System.setIn(originalSystemIn);
    System.setErr(originalSytstemErr);
  }

  @Test
  public void shouldExitAllLoadedValidators() {
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args = getCommandArguments(true, false, List.of());
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(
        keyManagerPubKey1, keyManagerPubKey2, validatorPubKey1, validatorPubKey2);
  }

  @Test
  public void shouldExitLoadedValidatorsUsingConfirmationMessage() {
    setUserInput("yes");
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args = getCommandArguments(true, true, List.of());
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(
        keyManagerPubKey1, keyManagerPubKey2, validatorPubKey1, validatorPubKey2);
  }

  @Test
  public void shouldExitValidatorWithPubKeyFromKeyManagerOnly() {
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args =
        getCommandArguments(
            true,
            false,
            List.of("--validator-public-keys", keyManagerPubKey2 + "," + nonExistingKey));

    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(keyManagerPubKey2);
    assertValidatorsNotExited(
        keyManagerPubKey1, validatorPubKey1, validatorPubKey2, nonExistingKey);
  }

  @Test
  public void shouldExitValidatorWithPubKeyFromPathOnly() {
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args =
        getCommandArguments(
            true,
            false,
            List.of("--validator-public-keys", validatorPubKey1 + "," + nonExistingKey));

    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));
    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);
    assertValidatorsNotExited(
        validatorPubKey2, keyManagerPubKey1, keyManagerPubKey2, nonExistingKey);
  }

  @Test
  public void shouldSkipKeyManagerKeys() {
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args =
        getCommandArguments(
            false,
            false,
            List.of("--validator-public-keys", validatorPubKey1 + "," + nonExistingKey));

    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(0);
    assertValidatorsExited(validatorPubKey1);
    assertValidatorsNotExited(
        validatorPubKey2, keyManagerPubKey1, keyManagerPubKey2, nonExistingKey);
  }

  @Test
  void shouldNotWarn_NotWithdrawableIfCapellaEnabled() {
    configureSuccessfulSpecResponse(mockBeaconServer, TestSpecFactory.createMinimalCapella());
    final List<String> args = getCommandArguments(false, true, List.of());
    setUserInput("no");

    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdOut.toString(UTF_8)).contains(VoluntaryExitCommand.WITHDRAWALS_PERMANENT_MESASGE);
    assertThat(stdOut.toString(UTF_8)).doesNotContain(VoluntaryExitCommand.WITHDRAWALS_NOT_ACTIVE);
    assertValidatorsNotExited(
        validatorPubKey1, validatorPubKey2, keyManagerPubKey1, keyManagerPubKey2, nonExistingKey);
  }

  @Test
  void shouldExitFailureWithNoValidatorKeysFound() {
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args = commandArgs.subList(0, 5);
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8)).contains("No validators were found to exit");
  }

  @Test
  void shouldExitFailureFutureEpoch() {
    configureSuccessfulSpecResponse(mockBeaconServer);
    final List<String> args = getCommandArguments(false, true, List.of("--epoch=1024"));
    int parseResult = beaconNodeCommand.parse(args.toArray(new String[0]));

    assertThat(parseResult).isEqualTo(1);
    assertThat(stdErr.toString(UTF_8))
        .contains("The specified epoch 1024 is greater than current epoch");
  }

  private void setUserInput(final String confirmationText) {
    String userInput =
        String.format(confirmationText, System.lineSeparator(), System.lineSeparator());
    ByteArrayInputStream inputStream = new ByteArrayInputStream(userInput.getBytes(UTF_8));
    System.setIn(inputStream);
  }

  private List<String> getCommandArguments(
      final boolean includeKeyManagerKeys,
      final boolean confirmationEnabled,
      final List<String> extraArgs) {
    final List<String> args = new ArrayList<>(commandArgs);
    args.add("--include-keymanager-keys=" + includeKeyManagerKeys);
    args.add("--confirmation-enabled=" + confirmationEnabled);

    args.addAll(extraArgs);
    return args;
  }

  private String getMockBeaconServerEndpoint(final ClientAndServer mockBeaconServer) {
    return String.format("http://127.0.0.1:%s/", mockBeaconServer.getLocalPort());
  }

  private void configureSuccessfulSpecResponse(final ClientAndServer mockBeaconServer) {
    configureSuccessfulSpecResponse(mockBeaconServer, TestSpecFactory.createMinimalBellatrix());
  }

  private void configureSuccessfulSpecResponse(
      final ClientAndServer mockBeaconServer, final Spec spec) {
    mockBeaconServer
        .when(request().withPath("/eth/v1/config/spec"))
        .respond(response().withStatusCode(200).withBody(getTestSpecResponse(spec)));
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

  private String getTestSpecResponse(final Spec spec) {
    return JsonBody.json(new ConfigProvider(spec).getConfig()).toString();
  }

  private String extractValidatorId(String pubKey) {
    return pubKey.substring(2, 9);
  }

  private void assertValidatorsExited(String... args) {
    Arrays.stream(args)
        .forEach(
            arg -> {
              assertThat(stdOut.toString(UTF_8))
                  .contains(String.format(exitSubmitted, extractValidatorId(arg)));
            });
  }

  private void assertValidatorsNotExited(String... args) {
    Arrays.stream(args)
        .forEach(
            arg -> {
              assertThat(stdOut.toString(UTF_8))
                  .doesNotContain(String.format(exitSubmitted, extractValidatorId(arg)));
            });
  }
}
