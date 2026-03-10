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

package tech.pegasys.teku.services.powchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;

class PowchainConfigurationTest {

  private final Spec spec = TestSpecFactory.createDefault();

  @Test
  public void buildShouldSetSeparateDepositSnapshotPathAndBundledDepositSnapshotPath() {
    final PowchainConfiguration config =
        PowchainConfiguration.builder()
            .specProvider(spec)
            .customDepositSnapshotPath("/tmp/foo")
            .setDepositSnapshotPathForNetwork(Optional.of(Eth2Network.MAINNET))
            .build();

    final DepositTreeSnapshotConfiguration depositTreeSnapshotConfiguration =
        config.getDepositTreeSnapshotConfiguration();
    assertThat(depositTreeSnapshotConfiguration.getCustomDepositSnapshotPath())
        .contains("/tmp/foo");
    assertThat(depositTreeSnapshotConfiguration.getBundledDepositSnapshotPath())
        .asString()
        .contains("mainnet.ssz");
  }

  @Test
  public void emptyNetworkShouldHaveEmptyBundledDepositSnapshotPath() {
    final PowchainConfiguration config =
        PowchainConfiguration.builder()
            .specProvider(spec)
            .setDepositSnapshotPathForNetwork(Optional.empty())
            .build();

    assertThat(config.getDepositTreeSnapshotConfiguration().getBundledDepositSnapshotPath())
        .isEmpty();
  }

  @Test
  public void whenDepositSnapshotIsDisabledBundledDepositSnapshotMustBeEmpty() {
    final PowchainConfiguration config =
        PowchainConfiguration.builder()
            .specProvider(spec)
            .depositSnapshotEnabled(false)
            // even though we are setting the value, because depositSnapshotEnabled = false it won't
            // be set
            .setDepositSnapshotPathForNetwork(Optional.of(Eth2Network.MAINNET))
            .build();

    assertThat(config.getDepositTreeSnapshotConfiguration().getBundledDepositSnapshotPath())
        .isEmpty();
    assertThat(config.getDepositTreeSnapshotConfiguration().isBundledDepositSnapshotEnabled())
        .isFalse();
  }
}
