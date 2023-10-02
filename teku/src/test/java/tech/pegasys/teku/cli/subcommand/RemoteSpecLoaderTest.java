/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.cli.subcommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.cli.subcommand.ValidatorClientCommand.DENEB_KZG_NOOP;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

class RemoteSpecLoaderTest {
  private final Spec spec = TestSpecFactory.createDefault();

  private final OkHttpValidatorRestApiClient apiClient = mock(OkHttpValidatorRestApiClient.class);

  @Test
  void shouldIgnoreUnknownConfigItems() {
    final Map<String, String> rawConfig = getRawConfigForSpec(spec);
    rawConfig.put("UNKNOWN_ITEM", "foo");
    when(apiClient.getConfigSpec()).thenReturn(Optional.of(new GetSpecResponse(rawConfig)));
    final Spec result = RemoteSpecLoader.getSpec(apiClient, modifier -> {});
    assertThat(getRawConfigForSpec(result)).containsExactlyInAnyOrderEntriesOf(rawConfig);
    assertThat(result.getGenesisSpecConfig()).isEqualTo(spec.getGenesisSpecConfig());
  }

  @Test
  void shouldFillWhenRequiredItemsAreMissing() {
    final Map<String, String> rawConfig = getRawConfigForSpec(spec);
    assertThat(rawConfig.remove("GENESIS_FORK_VERSION")).isNotNull();

    when(apiClient.getConfigSpec()).thenReturn(Optional.of(new GetSpecResponse(rawConfig)));

    assertThatThrownBy(() -> RemoteSpecLoader.getSpec(apiClient, modifier -> {}))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("GENESIS_FORK_VERSION");
  }

  @Test
  void shouldDefaultNetworkConfigThatMovedFromConstants() throws IOException {
    final String jsonConfig =
        Resources.toString(
            Resources.getResource(RemoteSpecLoaderTest.class, "config_missing_network_fields.json"),
            StandardCharsets.UTF_8);
    final ObjectMapper objectMapper = new ObjectMapper();
    TypeReference<Map<String, String>> typeReference = new TypeReference<>() {};
    Map<String, String> data = objectMapper.readValue(jsonConfig, typeReference);
    final SpecConfig specConfig = SpecConfigLoader.loadRemoteConfig(data, modifier -> {});

    // Check values not assigned, using default values
    assertThat(specConfig.getGossipMaxSize()).isEqualTo(10485760);
    assertThat(specConfig.getMaxChunkSize()).isEqualTo(10485760);
    assertThat(specConfig.getMaxRequestBlocks()).isEqualTo(1024);
    assertThat(specConfig.getEpochsPerSubnetSubscription()).isEqualTo(256);
    assertThat(specConfig.getMinEpochsForBlockRequests()).isEqualTo(33024);
    assertThat(specConfig.getTtfbTimeout()).isEqualTo(5);
    assertThat(specConfig.getRespTimeout()).isEqualTo(10);
    assertThat(specConfig.getAttestationPropagationSlotRange()).isEqualTo(32);
    assertThat(specConfig.getMaximumGossipClockDisparity()).isEqualTo(500);
  }

  @Test
  void shouldSetStubTrustedSetupPathForRemoteDeneb() {
    final Spec spec = TestSpecFactory.createMainnetDeneb();
    final Map<String, String> rawConfig = getRawConfigForSpec(spec);
    when(apiClient.getConfigSpec()).thenReturn(Optional.of(new GetSpecResponse(rawConfig)));

    final SpecConfig config =
        RemoteSpecLoader.getSpec(apiClient, DENEB_KZG_NOOP).getSpecConfig(UInt64.ONE);
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    assertThat(specConfigDeneb.isKZGNoop()).isTrue();
  }

  private Map<String, String> getRawConfigForSpec(final Spec spec) {
    return new ConfigProvider(spec).getConfig().data;
  }
}
