package tech.pegasys.teku.test.acceptance;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.BesuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuValidatorNode;
import tech.pegasys.teku.test.acceptance.dsl.Web3SignerNode;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

import java.util.Collections;

public class RemoteValidatorKeysAcceptanceTest  extends AcceptanceTestBase {
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
        createTekuNode(config -> config.withNetwork(networkName)
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

    validatorNodeApi.addRemoteValidatorsAndExpect(validatorKeystores.getPublicKeys(), signerApi.getValidatorUri().toString(), "imported");

    validatorClient.waitForLogMessageContaining("Added validator");
    validatorNodeApi.assertLocalValidatorListing(Collections.emptyList());
    validatorNodeApi.assertRemoteValidatorListing(validatorKeystores.getPublicKeys());

    // second add attempt would be duplicates (local add should see as duplicate too)
    validatorNodeApi.addLocalValidatorsAndExpect(validatorKeystores, "duplicate");
    validatorNodeApi.addRemoteValidatorsAndExpect(validatorKeystores.getPublicKeys(), signerApi.getValidatorUri().toString(), "duplicate");
    beaconNode.waitForEpoch(1);
    // TODO getting connect exceptions, so don't see any blocks created
    // validatorClient.waitForLogMessageContaining("Published block");

    validatorClient.stop();
    web3SignerNode.stop();
    beaconNode.stop();
    eth1Node.stop();
  }
}
