/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.beacon.pow.DepositSnapshotFileLoader.DEFAULT_SNAPSHOT_RESOURCE_PATHS;
import static tech.pegasys.teku.services.powchain.PowchainConfiguration.DEPOSIT_SNAPSHOT_URL_PATH;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.services.powchain.DepositTreeSnapshotConfiguration;
import tech.pegasys.teku.services.powchain.PowchainConfiguration;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class DepositOptionsTest extends AbstractBeaconNodeCommandTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"mainnet", "holesky", "gnosis", "sepolia"})
  public void shouldSetDefaultBundleSnapshotPathForSupportedNetwork(final String network) {
    final String[] args = {"--network=" + network, "--deposit-snapshot-enabled"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(
            config
                .powchain()
                .getDepositTreeSnapshotConfiguration()
                .isBundledDepositSnapshotEnabled())
        .isTrue();
    assertThat(
            config.powchain().getDepositTreeSnapshotConfiguration().getBundledDepositSnapshotPath())
        .contains(
            PowchainConfiguration.class
                .getResource(
                    DEFAULT_SNAPSHOT_RESOURCE_PATHS.get(
                        Eth2Network.fromStringLenient(network).get()))
                .toExternalForm());
  }

  @Test
  public void shouldHaveDepositSnapshotEnabledByDefault() {
    final String[] args = {};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(
            config
                .powchain()
                .getDepositTreeSnapshotConfiguration()
                .isBundledDepositSnapshotEnabled())
        .isTrue();
  }

  @Test
  public void shouldIgnoreBundleSnapshotPathForNotSupportedNetwork() {
    final String[] args = {"--network=swift", "--deposit-snapshot-enabled"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        config.powchain().getDepositTreeSnapshotConfiguration();
    assertThat(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).isTrue();
    assertThat(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath()).isEmpty();
    assertThat(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath()).isEmpty();
  }

  @Test
  public void shouldRespectDepositSnapshotFlagWhenDisabled() {
    final String[] args = {"--deposit-snapshot-enabled", "false"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(
            config
                .powchain()
                .getDepositTreeSnapshotConfiguration()
                .isBundledDepositSnapshotEnabled())
        .isFalse();
  }

  @Test
  public void shouldDisableBundledDepositSnapshotWhenUsingCustomDepositSnapshotPath() {
    final String[] args = {"--Xdeposit-snapshot", "/foo/bar"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        config.powchain().getDepositTreeSnapshotConfiguration();
    assertThat(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).isFalse();
    assertThat(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .hasValue("/foo/bar");
  }

  @Test
  public void shouldSetCheckpointSyncDepositSnapshotUrlWhenUsingCheckpointSyncUrl() {
    final String[] args = {"--checkpoint-sync-url", "http://checkpoint-sync.com"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        config.powchain().getDepositTreeSnapshotConfiguration();
    assertThat(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).isTrue();
    assertThat(depositTreeSnapshotConfiguration.getCheckpointSyncDepositSnapshotUrl())
        .hasValue("http://checkpoint-sync.com" + DEPOSIT_SNAPSHOT_URL_PATH);
  }

  @Test
  public void
      shouldDisableBundledDepositSnapshotWhenUsingCustomDepositSnapshotWithCheckpointSyncUrl() {
    final String[] args = {
      "--Xdeposit-snapshot", "/foo/bar", "--checkpoint-sync-url", "http://checkpoint-sync.com"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        config.powchain().getDepositTreeSnapshotConfiguration();
    assertThat(depositTreeSnapshotConfiguration.isBundledDepositSnapshotEnabled()).isFalse();
    assertThat(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .hasValue("/foo/bar");
  }
}
