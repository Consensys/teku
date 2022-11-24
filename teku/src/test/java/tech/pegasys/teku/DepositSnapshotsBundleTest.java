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

package tech.pegasys.teku;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.merkletree.DepositTree;
import tech.pegasys.teku.spec.networks.Eth2Network;

/** Checks consistency of bundled deposit snapshots */
public class DepositSnapshotsBundleTest extends AbstractBeaconNodeCommandTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("getAllNetworks")
  public void shouldHaveCorrectDepositsData_whenSnapshotLoaded(final Eth2Network eth2Network) {
    final String[] args = {
      "--network=" + eth2Network.configName(), "--Xdeposit-snapshot-bundle-enabled"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    if (config.powchain().getDepositSnapshotPath().isEmpty()) {
      return;
    }

    final DepositSnapshotFileLoader depositSnapshotLoader =
        new DepositSnapshotFileLoader(
            Optional.of(config.powchain().getDepositSnapshotPath().get()));
    final DepositTreeSnapshot depositTreeSnapshot =
        depositSnapshotLoader.loadDepositSnapshot().getDepositTreeSnapshot().get();
    final DepositTree depositTree = DepositTree.fromSnapshot(depositTreeSnapshot);
    assertThat(depositTree.getDepositCount()).isGreaterThan(0);
    assertThat(depositTree.getSnapshot().get()).isEqualTo(depositTreeSnapshot);
  }

  public static Stream<Eth2Network> getAllNetworks() {
    return Stream.of(Eth2Network.values());
  }
}
