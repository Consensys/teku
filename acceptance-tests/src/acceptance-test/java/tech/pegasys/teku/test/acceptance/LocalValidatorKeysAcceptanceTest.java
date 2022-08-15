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

public class LocalValidatorKeysAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldMaintainValidatorsInMutableClient() throws Exception {
    final String networkName = "less-swift";
    final BesuNode eth1Node = createBesuNode(config -> config.withMiningEnabled(true));
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
                    .withBeaconNode(beaconNode));
    final ValidatorKeysApi api = validatorClient.getValidatorKeysApi();

    beaconNode.start();
    validatorClient.start();

    api.assertLocalValidatorListing(Collections.emptyList());

    api.addLocalValidatorsAndExpect(validatorKeystores, "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.assertLocalValidatorListing(validatorKeystores.getPublicKeys());

    // second add attempt would be duplicates
    api.addLocalValidatorsAndExpect(validatorKeystores, "duplicate");

    // a random key won't be found, remove should give not_found
    api.removeLocalValidatorAndCheckStatus(extraKeys.getPublicKeys().get(0), "not_found");

    // Wait for a full epoch to pass so that all validators have attested
    // This ensures they have all generated slashing protection data
    beaconNode.waitForNextEpoch();
    beaconNode.waitForNextEpoch();

    // remove a validator
    final BLSPublicKey removedPubkey = validatorKeystores.getPublicKeys().get(0);
    api.removeLocalValidatorAndCheckStatus(removedPubkey, "deleted");

    // should only be 7 validators left
    validatorClient.waitForLogMessageContaining("Removed validator");
    validatorClient.waitForLogMessageContaining("Published block");
    api.assertLocalValidatorListing(validatorKeystores.getPublicKeys().subList(1, 7));

    // remove the same validator again
    api.removeLocalValidatorAndCheckStatus(removedPubkey, "not_active");

    validatorClient.stop();

    beaconNode.stop();
    eth1Node.stop();
  }
}
