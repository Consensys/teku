/*
 * Copyright 2022 ConsenSys AG.
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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.Web3SignerNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class RemoteValidatorKeysAcceptanceTest extends AcceptanceTestBase {
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
        createTekuNode(
            config ->
                config
                    .withNetwork(networkName)
                    .withDepositsFrom(eth1Node)
                    .withAltairEpoch(UInt64.MAX_VALUE));
    final Web3SignerNode web3SignerNode = createWeb3SignerNode(networkName);
    web3SignerNode.start();
    final ValidatorKeysApi signerApi = web3SignerNode.getValidatorKeysApi();

    final TekuValidatorNode validatorClient =
        createValidatorNode(
            config ->
                config
                    .withNetwork(networkName)
                    .withAltairEpoch(UInt64.MAX_VALUE)
                    .withValidatorApiEnabled()
                    .withExternalSignerUrl(web3SignerNode.getValidatorRestApiUrl())
                    .withInteropModeDisabled()
                    .withBeaconNode(beaconNode));

    beaconNode.start();
    validatorClient.start();

    signerApi.addLocalValidatorsAndExpect(validatorKeystores, "imported");
    signerApi.assertLocalValidatorListing(validatorKeystores.getPublicKeys());

    final ValidatorKeysApi validatorNodeApi = validatorClient.getValidatorKeysApi();

    validatorNodeApi.assertLocalValidatorListing(Collections.emptyList());
    validatorNodeApi.assertRemoteValidatorListing(Collections.emptyList());

    validatorNodeApi.addRemoteValidatorsAndExpect(
        validatorKeystores.getPublicKeys(), web3SignerNode.getValidatorRestApiUrl(), "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorNodeApi.assertLocalValidatorListing(Collections.emptyList());
    validatorNodeApi.assertRemoteValidatorListing(validatorKeystores.getPublicKeys());

    // second add attempt would be duplicates (local add should see as duplicate too)
    validatorNodeApi.addLocalValidatorsAndExpect(validatorKeystores, "duplicate");
    validatorNodeApi.addRemoteValidatorsAndExpect(
        validatorKeystores.getPublicKeys(), web3SignerNode.getValidatorRestApiUrl(), "duplicate");
    beaconNode.waitForEpoch(1);
    validatorClient.waitForLogMessageContaining("Published block");

    validatorClient.stop();
    web3SignerNode.stop();
    beaconNode.stop();
    eth1Node.stop();
  }
}
