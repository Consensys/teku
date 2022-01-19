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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;

class SpecConfigBuilderTest {

  @Test
  public void shouldLoadAltairForkEpoch() {
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.altairBuilder(
                    altairBuilder -> altairBuilder.altairForkEpoch(UInt64.valueOf(10))));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("ALTAIR_FORK_EPOCH"))
        .isEqualTo(UInt64.valueOf(10));
  }

  @Test
  public void shouldLoadBellatrixForkEpoch() {
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.bellatrixForkEpoch(UInt64.valueOf(683921))));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("BELLATRIX_FORK_EPOCH"))
        .isEqualTo(UInt64.valueOf(683921));
  }

  @Test
  public void shouldLoadTerminalTotalDifficulty() {
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder ->
                        mergeBuilder.terminalTotalDifficulty(UInt256.valueOf(12_000_000))));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("TERMINAL_TOTAL_DIFFICULTY"))
        .isEqualTo(UInt256.valueOf(12_000_000));
  }

  @Test
  public void shouldLoadTerminalBlockHash() {
    final Bytes32 randomBytes32 = Bytes32.random();
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.terminalBlockHash(randomBytes32)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("TERMINAL_BLOCK_HASH"))
        .isEqualTo(randomBytes32);
  }

  @Test
  public void shouldLoadTerminalBlockHashActivationEpoch() {
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder ->
                        mergeBuilder.terminalBlockHashActivationEpoch(UInt64.valueOf(7294629))));

    assertThat(
            spec.getGenesisSpec()
                .getConfig()
                .getRawConfig()
                .get("TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH"))
        .isEqualTo(UInt64.valueOf(7294629));
  }

  private Spec getSpec(Consumer<SpecConfigBuilder> consumer) {
    final SpecConfig config = SpecConfigLoader.loadConfig("mainnet", consumer);
    return SpecFactory.create(config);
  }
}
