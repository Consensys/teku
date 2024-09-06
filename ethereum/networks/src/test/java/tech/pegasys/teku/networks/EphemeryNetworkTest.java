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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
  //    private static final long CURRENT_TIMESTAMP = 1730119800;
  private static final long PERIOD_SINCE_GENESIS = 3;
  private static final int PERIOD = 28;
  private static final long PERIOD_IN_SECONDS = (PERIOD * 24 * 60 * 60);
  private long expectedUpdatedTimestamp;
  private long expectedUpdatedChainId;
  private final SpecConfigReader reader = new SpecConfigReader();

  @BeforeEach
  void setUp() {
    expectedUpdatedTimestamp = GENESIS_TIMESTAMP + (PERIOD_SINCE_GENESIS * PERIOD_IN_SECONDS);
    expectedUpdatedChainId = GENESIS_CHAINID + PERIOD_SINCE_GENESIS;
  }

  @Test
  public void testUpdateConfig() {
    final SpecConfigBuilder builder = new SpecConfigBuilder();
    final SpecConfig config = SpecConfigLoader.loadConfig("ephemery");

    builder.rawConfig(config.getRawConfig()).depositNetworkId(expectedUpdatedChainId);
    builder.rawConfig(config.getRawConfig()).depositChainId(expectedUpdatedChainId);
    builder
        .rawConfig(config.getRawConfig())
        .minGenesisTime(UInt64.valueOf(expectedUpdatedTimestamp));

    final String expectedDepositChainId =
        String.valueOf(builder.rawConfig(config.getRawConfig()).build().getDepositChainId());
    final String expectedDepositNetworkId =
        String.valueOf(builder.rawConfig(config.getRawConfig()).build().getDepositNetworkId());
    final String expectedMinGenesisTime =
        String.valueOf(builder.rawConfig(config.getRawConfig()).build().getMinGenesisTime());

    assertEquals(String.valueOf(expectedUpdatedChainId), expectedDepositChainId);
    assertEquals(String.valueOf(expectedUpdatedChainId), expectedDepositNetworkId);
    assertEquals(String.valueOf(expectedUpdatedTimestamp), String.valueOf(expectedMinGenesisTime));
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
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
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

  //    private UInt256 randomUInt256() {
  //        return UInt256.fromBytes(Bytes.wrap(randomBytes(32)));
  //    }

}
