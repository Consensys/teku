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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.config.GetSpecResponse;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;

class ConfigProviderTest {
  private final Spec spec = SpecFactory.create("prater");

  private final ConfigProvider configProvider = new ConfigProvider(spec);

  @Test
  void shouldParseResultOfConfig() {
    final GetSpecResponse response = configProvider.getConfig();
    final SpecConfig specConfig = SpecConfigLoader.loadRemoteConfig(response.data);
    final SpecConfig expectedConfig = spec.getGenesisSpecConfig();
    assertThat(specConfig).isEqualToComparingFieldByField(expectedConfig);
  }
}
