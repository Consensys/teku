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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class ValidatorKeysAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldMaintainValidatorsInMutableClient() throws Exception {
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
    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();

    beaconNode.start();
    validatorClient.start();

    api.assertValidatorListing(Collections.emptyList());

    api.addValidatorsAndExpect(validatorKeystores, "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.assertValidatorListing(validatorKeystores.getPublicKeys());

    // second add attempt would be duplicates
    api.addValidatorsAndExpect(validatorKeystores, "duplicate");

    // a random key won't be found, remove should give not_found
    api.removeValidatorAndCheckStatus(extraKeys.getPublicKeys().get(0), "not_found");

    beaconNode.waitForEpoch(2);
    // remove a validator
    final BLSPublicKey removedPubkey = validatorKeystores.getPublicKeys().get(0);
    api.removeValidatorAndCheckStatus(removedPubkey, "deleted");

    // should only be 7 validators left
    validatorClient.waitForLogMessageContaining("Removed validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.assertValidatorListing(validatorKeystores.getPublicKeys().subList(1, 7));

    // remove the same validator again
    api.removeValidatorAndCheckStatus(removedPubkey, "not_active");

    validatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }
}
