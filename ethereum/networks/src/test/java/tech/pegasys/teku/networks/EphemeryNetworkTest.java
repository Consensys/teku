/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networks;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.SpecConfigReader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;

public class EphemeryNetworkTest {
  private static final long GENESIS_CHAINID = 39438135;
  private static final long ONE_PERIOD = 1;
  private static final int MANY_PERIOD = 1000;
  private static final long MIN_GENESIS_TIME = 1720119600;
  private final SystemTimeProvider timeProvider = new SystemTimeProvider();

  private SpecConfigBuilder builder;
  private static final long CURRENT_TIMESTAMP = 1725547734;
  private static final int PERIOD = 28;
  private static final long PERIOD_IN_SECONDS = (PERIOD * 24 * 60 * 60);
  private long expectedMinGenesisTime;
  private long expectedChainId;
  private long periodSinceGenesis;
  private final SpecConfigReader reader = new SpecConfigReader();
  private SpecConfigAndParent<? extends SpecConfig> configFile;
  private SpecConfig config;

  @BeforeEach
  void setUp() {
    periodSinceGenesis = EphemeryNetwork.getPeriodsSinceGenesis(timeProvider);
    expectedMinGenesisTime = MIN_GENESIS_TIME + (periodSinceGenesis * PERIOD_IN_SECONDS);
    expectedChainId = GENESIS_CHAINID + periodSinceGenesis;
    builder = mock(SpecConfigBuilder.class);
    configFile = SpecConfigLoader.loadConfig("ephemery");
    config = mock(SpecConfig.class);
  }

  @Test
  public void testUpdateConfig() {
    when(config.getRawConfig()).thenReturn(configFile.specConfig().getRawConfig());
    when(builder.rawConfig(config.getRawConfig())).thenReturn(builder);
    when(builder.depositChainId(expectedChainId)).thenReturn(builder);
    when(builder.depositNetworkId(expectedChainId)).thenReturn(builder);
    when(builder.minGenesisTime(UInt64.valueOf(expectedMinGenesisTime))).thenReturn(builder);

    EphemeryNetwork.updateConfig(builder);

    verify(builder, times(1)).rawConfig(config.getRawConfig()); // Only verify once
    verify(builder, times(1)).depositNetworkId(expectedChainId);
    verify(builder, times(1)).depositChainId(expectedChainId);
    verify(builder, times(1)).minGenesisTime(UInt64.valueOf(expectedMinGenesisTime));
    assertThat(CURRENT_TIMESTAMP).isGreaterThan(GENESIS_CHAINID + PERIOD_IN_SECONDS);
  }

  @Test
  public void shouldLoadDepositNetworkId() {
    final Spec spec = getSpec(phase0Builder -> phase0Builder.depositNetworkId(GENESIS_CHAINID));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("DEPOSIT_NETWORK_ID"))
        .isEqualTo(String.valueOf(GENESIS_CHAINID));
  }

  @Test
  public void shouldLoadDepositChainId() {
    final Spec spec = getSpec(phase0Builder -> phase0Builder.depositChainId(GENESIS_CHAINID));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("DEPOSIT_CHAIN_ID"))
        .isEqualTo(String.valueOf(GENESIS_CHAINID));
  }

  @Test
  public void shouldLoadMinGenesisTime() {
    final Spec spec =
        getSpec(phase0Builder -> phase0Builder.minGenesisTime(UInt64.valueOf(MIN_GENESIS_TIME)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("MIN_GENESIS_TIME"))
        .isEqualTo(String.valueOf(MIN_GENESIS_TIME));
  }

  @Test
  public void read_missingConfig() {
    processFileAsInputStream(getInvalidConfigPath("missingChurnLimit"), this::readConfig);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MIN_PER_EPOCH_CHURN_LIMIT");
  }

  @Test
  void shouldNotUpdateConfigBeforeGenesis() {
    final Spec spec =
        getSpec(phase0Builder -> phase0Builder.minGenesisTime(UInt64.valueOf(MIN_GENESIS_TIME)));

    final long preGenesisTime = MIN_GENESIS_TIME - ONE_PERIOD;
    final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(preGenesisTime);
    EphemeryNetwork.updateConfig(builder, stubTimeProvider);

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("MIN_GENESIS_TIME"))
        .isEqualTo(String.valueOf(MIN_GENESIS_TIME));
    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("DEPOSIT_CHAIN_ID"))
        .isEqualTo(String.valueOf(GENESIS_CHAINID));
    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("DEPOSIT_NETWORK_ID"))
        .isEqualTo(String.valueOf(GENESIS_CHAINID));
  }

  @Test
  public void shouldUpdateConfigAfterFirstPeriod() {

    final long onePeriodSinceGenesis = MIN_GENESIS_TIME + PERIOD_IN_SECONDS + ONE_PERIOD;

    final StubTimeProvider stubTimeProvider =
        StubTimeProvider.withTimeInSeconds(onePeriodSinceGenesis);
    final long genesisChainidAfterFirstPeriod = GENESIS_CHAINID + ONE_PERIOD;

    final long expectedMinGenesisTime = MIN_GENESIS_TIME + (ONE_PERIOD * PERIOD_IN_SECONDS);

    when(config.getRawConfig()).thenReturn(configFile.specConfig().getRawConfig());
    when(builder.rawConfig(config.getRawConfig())).thenReturn(builder);
    when(builder.depositChainId(genesisChainidAfterFirstPeriod)).thenReturn(builder);
    when(builder.depositNetworkId(genesisChainidAfterFirstPeriod)).thenReturn(builder);
    when(builder.minGenesisTime(UInt64.valueOf(expectedMinGenesisTime))).thenReturn(builder);

    EphemeryNetwork.updateConfig(builder, stubTimeProvider);

    verify(builder, times(1)).rawConfig(config.getRawConfig()); // Only verify once
    verify(builder, times(1)).depositNetworkId(genesisChainidAfterFirstPeriod);
    verify(builder, times(1)).depositChainId(genesisChainidAfterFirstPeriod);
    verify(builder, times(1)).minGenesisTime(UInt64.valueOf(expectedMinGenesisTime));
    assertThat(CURRENT_TIMESTAMP).isGreaterThan(genesisChainidAfterFirstPeriod + PERIOD_IN_SECONDS);
  }

  @Test
  public void shouldUpdateConfigForManyPeriods() {

    final long timeFor1000Periods = MIN_GENESIS_TIME + (MANY_PERIOD * PERIOD_IN_SECONDS);
    final StubTimeProvider stubTimeProvider =
        StubTimeProvider.withTimeInSeconds(timeFor1000Periods);

    final long genesisChainIdAfter1000Period = GENESIS_CHAINID + MANY_PERIOD;

    final long expectedMinGenesisTime = MIN_GENESIS_TIME + MANY_PERIOD * PERIOD_IN_SECONDS;

    when(config.getRawConfig()).thenReturn(configFile.specConfig().getRawConfig());
    when(builder.rawConfig(config.getRawConfig())).thenReturn(builder);
    when(builder.depositChainId(genesisChainIdAfter1000Period)).thenReturn(builder);
    when(builder.depositNetworkId(genesisChainIdAfter1000Period)).thenReturn(builder);
    when(builder.minGenesisTime(UInt64.valueOf(expectedMinGenesisTime))).thenReturn(builder);

    EphemeryNetwork.updateConfig(builder, stubTimeProvider);

    verify(builder, times(1)).rawConfig(config.getRawConfig()); // Only verify once
    verify(builder, times(1)).depositNetworkId(genesisChainIdAfter1000Period);
    verify(builder, times(1)).depositChainId(genesisChainIdAfter1000Period);
    verify(builder, times(1)).minGenesisTime(UInt64.valueOf(expectedMinGenesisTime));

    assertThat(expectedMinGenesisTime).isGreaterThan(MIN_GENESIS_TIME);
    assertThat(genesisChainIdAfter1000Period).isGreaterThan(GENESIS_CHAINID);
    assertThat(CURRENT_TIMESTAMP + expectedMinGenesisTime)
        .isGreaterThan(genesisChainIdAfter1000Period + PERIOD_IN_SECONDS);
  }

  @Test
  void checkEphemeryMaxSlot() {
    final Spec spec =
        getSpec(phase0Builder -> phase0Builder.minGenesisTime(UInt64.valueOf(MIN_GENESIS_TIME)));
    final long timeBeforeNextPeriod = MIN_GENESIS_TIME + PERIOD_IN_SECONDS - 1;
    final MiscHelpers miscHelpers = new MiscHelpers(spec.getGenesisSpecConfig());
    assertThat(
            miscHelpers
                .computeSlotAtTime(
                    UInt64.valueOf(MIN_GENESIS_TIME), UInt64.valueOf(timeBeforeNextPeriod))
                .longValue())
        .isEqualTo(EphemeryNetwork.MAX_EPHEMERY_SLOT);
  }

  @Test
  void shouldNotUpdateConfigBeforeNextPeriod() {
    final Spec spec =
        getSpec(phase0Builder -> phase0Builder.minGenesisTime(UInt64.valueOf(MIN_GENESIS_TIME)));
    final long timeBeforeNextPeriod = MIN_GENESIS_TIME + PERIOD_IN_SECONDS - 1;
    final StubTimeProvider stubTimeProvider =
        StubTimeProvider.withTimeInSeconds(timeBeforeNextPeriod);

    EphemeryNetwork.updateConfig(builder, stubTimeProvider);

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("MIN_GENESIS_TIME"))
        .isEqualTo(String.valueOf(MIN_GENESIS_TIME));
    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("DEPOSIT_CHAIN_ID"))
        .isEqualTo(String.valueOf(GENESIS_CHAINID));
    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("DEPOSIT_NETWORK_ID"))
        .isEqualTo(String.valueOf(GENESIS_CHAINID));
  }

  private static String getInvalidConfigPath(final String name) {
    return getConfigPath(name);
  }

  private static String getConfigPath(final String name) {
    final String path = "tech/pegasys/teku/networks/";
    return path + name + ".yaml";
  }

  private void processFileAsInputStream(final String fileName, final InputStreamHandler handler) {
    try (final InputStream inputStream = getFileFromResourceAsStream(fileName)) {
      handler.accept(inputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream getFileFromResourceAsStream(final String fileName) {
    final InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
    if (inputStream == null) {
      throw new IllegalArgumentException("File not found: " + fileName);
    }

    return inputStream;
  }

  private interface InputStreamHandler {
    void accept(InputStream inputStream) throws IOException;
  }

  private void readConfig(final InputStream preset) throws IOException {
    reader.readAndApply(preset, false);
  }

  private Spec getSpec(final Consumer<SpecConfigBuilder> consumer) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig("ephemery", consumer);
    return SpecFactory.create(config);
  }
}
