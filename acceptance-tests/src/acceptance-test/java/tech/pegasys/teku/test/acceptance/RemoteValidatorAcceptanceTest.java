/*
 * Copyright 2020 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;

public class RemoteValidatorAcceptanceTest extends AcceptanceTestBase {
  @Test
  void shouldCreateAttestationsWithRemoteValidator() throws Exception {
    final int VALIDATOR_COUNT = 8;
    final TekuNode node1 =
        createTekuNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withInteropNumberOfValidators(VALIDATOR_COUNT)
                    .withInteropValidators(0, 0)
                    .withRestHostsAllowed("*"));

    final TekuValidatorNode validatorNode1 =
        createValidatorNode(
            config ->
                config
                    .withNetwork("minimal")
                    .withInteropValidators(0, VALIDATOR_COUNT)
                    .withBeaconNodeEndpoint(node1.getBeaconRestApiUrl()));

    node1.start();
    validatorNode1.start();
    node1.waitForSlot(3);

    assertThat(validatorNode1.getLoggedErrors()).isEmpty();
    assertThat(validatorNode1.getFilteredOutput("Published aggregate").size())
        .isGreaterThanOrEqualTo(2);
    assertThat(validatorNode1.getFilteredOutput("Published attestation").size())
        .isGreaterThanOrEqualTo(2);
    assertThat(validatorNode1.getFilteredOutput("Published block").size())
        .isGreaterThanOrEqualTo(2);
  }
}
