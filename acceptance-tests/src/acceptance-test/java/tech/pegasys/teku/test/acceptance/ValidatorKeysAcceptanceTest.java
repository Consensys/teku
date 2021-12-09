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

import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorKeysAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldMaintainValidatorsInMutableClient(@TempDir final Path tempDir) throws Exception {
    final ValidatorKeysApi api = new ValidatorKeysApi(tempDir);
    final String networkName = "less-swift";
    final BesuNode eth1Node = createBesuNode();
    eth1Node.start();

    final ValidatorKeystores validatorKeystores =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 8);
    final ValidatorKeystores extraKeys =
        createTekuDepositSender(networkName).sendValidatorDeposits(eth1Node, 1);

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
            .withValidatorApiEnabled();

    beaconNode.start();
    validatorClient.start();

    api.getValidatorListing(validatorClient, Collections.emptyList());

    api.addValidatorsAndExpect(validatorClient, validatorKeystores, "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.getValidatorListing(validatorClient, validatorKeystores.getPublicKeys());

    // second add attempt would be duplicates
    api.addValidatorsAndExpect(validatorClient, validatorKeystores, "duplicate");

    // a random key won't be found, remove should give not_found
    api.removeValidatorAndCheckStatus(
        validatorClient, extraKeys.getPublicKeys().get(0), "not_found");

    beaconNode.waitForEpoch(2);
    // remove a validator
    final BLSPublicKey removedPubkey = validatorKeystores.getPublicKeys().get(0);
    api.removeValidatorAndCheckStatus(validatorClient, removedPubkey, "deleted");

    // should only be 7 validators left
    validatorClient.waitForLogMessageContaining("Removed validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.getValidatorListing(validatorClient, validatorKeystores.getPublicKeys().subList(1, 7));

    // remove the same validator again
    api.removeValidatorAndCheckStatus(validatorClient, removedPubkey, "not_active");

    validatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }
}
