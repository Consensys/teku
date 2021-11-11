/*
 * Copyright 2021 ConsenSys AG.
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

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpValidatorRestApiClient;

class RemoteSpecLoaderTest {
  private final Spec spec = TestSpecFactory.createDefault();

  private final OkHttpValidatorRestApiClient apiClient = mock(OkHttpValidatorRestApiClient.class);

  @Test
  void shouldIgnoreUnknownConfigItems() {
    final Map<String, String> rawConfig = getRawConfigForSpec(spec);
    rawConfig.put("UNKNOWN_ITEM", "foo");
    when(apiClient.getConfigSpec()).thenReturn(Optional.of(new GetSpecResponse(rawConfig)));
    final Spec result = RemoteSpecLoader.getSpec(apiClient);
    assertThat(getRawConfigForSpec(result)).containsExactlyInAnyOrderEntriesOf(rawConfig);
    assertThat(result.getGenesisSpecConfig()).isEqualTo(spec.getGenesisSpecConfig());
  }

  @Test
  void shouldFailWhenRequiredItemsAreMissing() {
    final Map<String, String> rawConfig = getRawConfigForSpec(spec);
    assertThat(rawConfig.remove("GENESIS_FORK_VERSION")).isNotNull();

    when(apiClient.getConfigSpec()).thenReturn(Optional.of(new GetSpecResponse(rawConfig)));

    assertThatThrownBy(() -> RemoteSpecLoader.getSpec(apiClient))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessageContaining("GENESIS_FORK_VERSION");
  }

  private Map<String, String> getRawConfigForSpec(final Spec spec) {
    return new ConfigProvider(spec).getConfig().data;
  }
}
