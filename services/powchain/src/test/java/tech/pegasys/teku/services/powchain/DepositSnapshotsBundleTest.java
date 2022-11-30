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

package tech.pegasys.teku.services.powchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ReadonlyTransactionManager;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader;
import tech.pegasys.teku.beacon.pow.contract.DepositContract;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.merkletree.DepositTree;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;
import tech.pegasys.teku.spec.networks.Eth2Network;

/** Checks consistency of bundled deposit snapshots */
public class DepositSnapshotsBundleTest {
  private static final Spec SPEC = TestSpecFactory.createDefault();
  private static final String INFURA_KEY_ENV = "INFURA_KEY";
  public static final Map<Eth2Network, String> SUPPORTED_REMOTE_VERIFICATION_NETWORK_PREFIXES =
      Map.of(
          Eth2Network.PRATER, "goerli",
          Eth2Network.MAINNET, "mainnet",
          Eth2Network.SEPOLIA, "sepolia");

  @ParameterizedTest(name = "{0}")
  @MethodSource("getSupportedNetworks")
  public void shouldCreateCorrectDepositTreeSnapshotFromEachBundleSnapshot(
      final Eth2Network eth2Network) {
    final PowchainConfiguration.Builder powchainConfigBuilder = PowchainConfiguration.builder();
    powchainConfigBuilder
        .specProvider(SPEC)
        .depositSnapshotEnabled(true)
        .setDepositSnapshotPathForNetwork(Optional.of(eth2Network));
    final PowchainConfiguration powchainConfiguration = powchainConfigBuilder.build();
    if (powchainConfiguration.getDepositSnapshotPath().isEmpty()) {
      return;
    }

    final DepositSnapshotFileLoader depositSnapshotLoader =
        new DepositSnapshotFileLoader(
            Optional.of(powchainConfiguration.getDepositSnapshotPath().get()));
    final DepositTreeSnapshot depositTreeSnapshot =
        depositSnapshotLoader.loadDepositSnapshot().getDepositTreeSnapshot().get();
    final DepositTree depositTree = DepositTree.fromSnapshot(depositTreeSnapshot);
    assertThat(depositTree.getDepositCount()).isGreaterThan(0);
    assertThat(depositTree.getSnapshot().get()).isEqualTo(depositTreeSnapshot);
  }

  @Disabled("Executed for manual verification, slow for CI")
  @ParameterizedTest(name = "{0}")
  @MethodSource("getSupportedNetworks")
  public void shouldValidateSnapshotViaRemoteAPI(final Eth2Network eth2Network) throws Exception {
    final PowchainConfiguration.Builder powchainConfigBuilder = PowchainConfiguration.builder();
    powchainConfigBuilder
        .specProvider(SPEC)
        .depositSnapshotEnabled(true)
        .setDepositSnapshotPathForNetwork(Optional.of(eth2Network));
    final PowchainConfiguration powchainConfiguration = powchainConfigBuilder.build();

    final String depositSnapshotPath = powchainConfiguration.getDepositSnapshotPath().get();
    final DepositSnapshotFileLoader depositSnapshotLoader =
        new DepositSnapshotFileLoader(Optional.of(depositSnapshotPath));

    // Snapshot
    final DepositTreeSnapshot depositTreeSnapshot =
        depositSnapshotLoader.loadDepositSnapshot().getDepositTreeSnapshot().orElseThrow();

    final Eth2NetworkConfiguration networkConfig =
        Eth2NetworkConfiguration.builder(eth2Network).build();
    final OkHttpClient httpClient =
        new OkHttpClient.Builder().connectionPool(new ConnectionPool()).build();
    final String infuraKey = System.getenv(INFURA_KEY_ENV);
    if (infuraKey == null) {
      throw new RuntimeException("Configure Infura key with environmental variable INFURA_KEY");
    }
    final String endpoint =
        "https://"
            + SUPPORTED_REMOTE_VERIFICATION_NETWORK_PREFIXES.get(eth2Network)
            + ".infura.io/v3/"
            + infuraKey;
    final Web3j web3j = Web3j.build(new HttpService(endpoint, httpClient));
    final DepositContract depositContract =
        DepositContract.load(
            networkConfig.getEth1DepositContractAddress().toHexString(),
            web3j,
            new ReadonlyTransactionManager(
                web3j, networkConfig.getEth1DepositContractAddress().toHexString()),
            null);

    // Query deposit contract at DepositTreeSnapshot block height
    depositContract.setDefaultBlockParameter(
        DefaultBlockParameter.valueOf(
            depositTreeSnapshot.getExecutionBlockHeight().bigIntegerValue()));

    // Verify deposit_root
    final Bytes32 expectedRoot = Bytes32.wrap(depositContract.getDepositRoot().send().getValue());
    assertThat(depositTreeSnapshot.getDepositRoot()).isEqualTo(expectedRoot);

    // Verify deposit_count
    final Bytes depositCountBytes = Bytes.wrap(depositContract.getDepositCount().send().getValue());
    final UInt64 depositCount = MathHelpers.bytesToUInt64(depositCountBytes);
    assertThat(depositTreeSnapshot.getDepositCount()).isEqualTo(depositCount.longValue());

    // Check that the block hash is the canonical block at the expected block height
    final EthBlock block =
        web3j
            .ethGetBlockByNumber(
                DefaultBlockParameter.valueOf(
                    depositTreeSnapshot.getExecutionBlockHeight().bigIntegerValue()),
                false)
            .send();
    assertThat(block.getBlock().getHash())
        .isEqualTo(depositTreeSnapshot.getExecutionBlockHash().toHexString());
  }

  public static Stream<Eth2Network> getSupportedNetworks() {
    return SUPPORTED_REMOTE_VERIFICATION_NETWORK_PREFIXES.keySet().stream();
  }
}
