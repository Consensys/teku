/*
 * Copyright Consensys Software Inc., 2024
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
import static tech.pegasys.teku.networks.EphemeryNetwork.PERIODS_SINCE_GENESIS;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.SpecConfigReader;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;

public class EphemeryNetworkTest {
  private static final long GENESIS_CHAINID = 39438135;
  private static final long GENESIS_TIMESTAMP = 1720119600;
  private SpecConfigBuilder builder;
  private static final long CURRENT_TIMESTAMP = 1725547734;
  private static final int PERIOD = 28;
  private static final long PERIOD_IN_SECONDS = (PERIOD * 24 * 60 * 60);
  private long expectedMinGenesisTime;
  private long expectedChainId;
  private final SpecConfigReader reader = new SpecConfigReader();

  @BeforeEach
  void setUp() {
    expectedMinGenesisTime = GENESIS_TIMESTAMP + (PERIODS_SINCE_GENESIS * PERIOD_IN_SECONDS);
    expectedChainId = GENESIS_CHAINID + PERIODS_SINCE_GENESIS;
    builder = mock(SpecConfigBuilder.class);
  }

  @Test
  public void testUpdateConfig() {
    final SpecConfig configFile = SpecConfigLoader.loadConfig("ephemery");
    final SpecConfig config = mock(SpecConfig.class);

    when(config.getRawConfig()).thenReturn(configFile.getRawConfig());
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
        getSpec(phase0Builder -> phase0Builder.minGenesisTime(UInt64.valueOf(GENESIS_TIMESTAMP)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("MIN_GENESIS_TIME"))
        .isEqualTo(String.valueOf(GENESIS_TIMESTAMP));
  }

  @Test
  public void read_missingConfig() {
    processFileAsInputStream(getInvalidConfigPath("missingChurnLimit"), this::readConfig);

    assertThatThrownBy(reader::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MIN_PER_EPOCH_CHURN_LIMIT");
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
    final SpecConfig config = SpecConfigLoader.loadConfig("ephemery", consumer);
    return SpecFactory.create(config);
  }
}
