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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SpecConfigBuilderTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void shouldLoadAltairForkEpoch() {
    final UInt64 randomEpoch = dataStructureUtil.randomUInt64(100_000);
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder
                    .altairBuilder(altairBuilder -> altairBuilder.altairForkEpoch(randomEpoch))
                    .bellatrixBuilder(
                        bellatrixBuilder ->
                            bellatrixBuilder.bellatrixForkEpoch(randomEpoch.plus(1))));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("ALTAIR_FORK_EPOCH"))
        .isEqualTo(randomEpoch);
  }

  @Test
  public void shouldLoadBellatrixForkEpoch() {
    final UInt64 randomEpoch = dataStructureUtil.randomUInt64(100_000);
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.bellatrixForkEpoch(randomEpoch)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("BELLATRIX_FORK_EPOCH"))
        .isEqualTo(randomEpoch);
  }

  @Test
  public void shouldLoadTerminalTotalDifficulty() {
    final UInt256 randomUInt256 = dataStructureUtil.randomUInt256();
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.terminalTotalDifficulty(randomUInt256)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("TERMINAL_TOTAL_DIFFICULTY"))
        .isEqualTo(randomUInt256);
  }

  @Test
  public void shouldLoadTerminalBlockHash() {
    final Bytes32 randomBytes32 = dataStructureUtil.randomBytes32();
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
    final UInt64 randomUInt64 = dataStructureUtil.randomUInt64();
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.terminalBlockHashActivationEpoch(randomUInt64)));

    assertThat(
            spec.getGenesisSpec()
                .getConfig()
                .getRawConfig()
                .get("TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH"))
        .isEqualTo(randomUInt64);
  }

  private Spec getSpec(Consumer<SpecConfigBuilder> consumer) {
    final SpecConfig config = SpecConfigLoader.loadConfig("mainnet", consumer);
    return SpecFactory.create(config);
  }
}
