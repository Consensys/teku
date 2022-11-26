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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.migrated.BlockHeaderData;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.merkletree.DepositTree;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.ChainDataLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

/** Checks consistency of bundled deposit snapshots */
public class DepositSnapshotsBundleTest {
  private static final Spec SPEC = TestSpecFactory.createDefault();

  @ParameterizedTest(name = "{0}")
  @MethodSource("getAllNetworks")
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

  @Disabled("Remove to verify updated deposit snapshots")
  @ParameterizedTest(name = "{0}")
  @MethodSource("getAllNetworks")
  public void shouldCheckConsistencyOfSnapshotAndBlockHeaderAndState(final Eth2Network eth2Network)
      throws IOException {
    final PowchainConfiguration.Builder powchainConfigBuilder = PowchainConfiguration.builder();
    powchainConfigBuilder
        .specProvider(SPEC)
        .depositSnapshotEnabled(true)
        .setDepositSnapshotPathForNetwork(Optional.of(eth2Network));
    final PowchainConfiguration powchainConfiguration = powchainConfigBuilder.build();
    if (powchainConfiguration.getDepositSnapshotPath().isEmpty()) {
      return;
    }

    final String depositSnapshotPath = powchainConfiguration.getDepositSnapshotPath().get();
    final DepositSnapshotFileLoader depositSnapshotLoader =
        new DepositSnapshotFileLoader(Optional.of(depositSnapshotPath));

    // Snapshot
    final DepositTreeSnapshot depositTreeSnapshot =
        depositSnapshotLoader.loadDepositSnapshot().getDepositTreeSnapshot().get();

    // Block header
    final String snapshotNoSuffixPath =
        depositSnapshotPath.substring(0, depositSnapshotPath.length() - 4);
    final String blockHeaderPath = snapshotNoSuffixPath + "_header.json";
    final String headerJson =
        new String(loadFromUrl(blockHeaderPath).toArray(), StandardCharsets.UTF_8);
    BlockHeaderData blockHeaderData =
        JsonUtil.parse(headerJson, BlockHeaderData.getJsonTypeDefinition());

    // State
    final String statePath = snapshotNoSuffixPath + "_state.ssz";
    final Spec spec = SpecFactory.create(eth2Network.configName());
    final BeaconState state = ChainDataLoader.loadState(spec, statePath);

    // Assertions
    assertThat(blockHeaderData.getHeader().getMessage().getStateRoot())
        .isEqualTo(state.hashTreeRoot());
    final Eth1Data eth1Data = state.getEth1Data();
    assertThat(depositTreeSnapshot.getDepositCount())
        .isEqualTo(eth1Data.getDepositCount().longValue());
    assertThat(depositTreeSnapshot.getDepositRoot()).isEqualTo(eth1Data.getDepositRoot());
    assertThat(depositTreeSnapshot.getExecutionBlockHash()).isEqualTo(eth1Data.getBlockHash());
  }

  private Bytes loadFromUrl(final String path) throws IOException {
    return ResourceLoader.urlOrFile("application/octet-stream")
        .loadBytes(path)
        .orElseThrow(() -> new FileNotFoundException(String.format("File '%s' not found", path)));
  }

  public static Stream<Eth2Network> getAllNetworks() {
    return Stream.of(Eth2Network.values());
  }
}
