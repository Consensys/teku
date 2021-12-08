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

package tech.pegasys.teku.test.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ValidatorKeysAcceptanceTest extends AcceptanceTestBase {
  private final JsonProvider jsonProvider = new JsonProvider();
  @Test
  void shouldAddValidatorToRunningClient(@TempDir final Path tempDir) throws Exception {
    final String networkName = "less-swift";
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 8);

    final TekuNode beaconNode =
        createTekuNode(config -> config.withNetwork(networkName).withDepositsFrom(eth1Node));

    final TekuValidatorNode validatorClient =
        createValidatorNode(
                config ->
                    config
                        .withNetwork(networkName)
                        .withValidatorApiEnabled()
                        .withInteropModeDisabled()
                        .withBeaconNode(beaconNode))
            .withMutableValidatorKeystores(validatorKeystores);

    beaconNode.start();
    validatorClient.start();

    final JsonNode data = getValidatorListing(validatorClient);
    assertThat(data.isArray()).isTrue();
    assertThat(data.size()).isZero();

    final JsonNode addResult = jsonProvider.getObjectMapper().readTree(validatorClient.addValidators(validatorKeystores, tempDir));
    assertThat(addResult.get("data").size()).isEqualTo(8);

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorClient.waitForLogMessageContaining("Published block");

    final JsonNode removeResult = jsonProvider.getObjectMapper().readTree(validatorClient.removeValidator(validatorKeystores.getPublicKeys().get(0)));
    assertThat(removeResult.get("data").size()).isEqualTo(1);
    validatorClient.waitForLogMessageContaining("Deleted validator");

    validatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }

  private JsonNode getValidatorListing(final TekuValidatorNode validatorClient) throws IOException {
    final JsonNode result = jsonProvider.getObjectMapper().readTree(validatorClient.getValidatorListing());
    final JsonNode data = result.get("data");
    assertThat(data.isArray()).isTrue();
    return data;

  }
}
