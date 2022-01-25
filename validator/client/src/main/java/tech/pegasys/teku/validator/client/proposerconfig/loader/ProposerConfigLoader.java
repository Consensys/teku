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

package tech.pegasys.teku.validator.client.proposerconfig.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.validator.client.ProposerConfig;

public class ProposerConfigLoader {
  final ObjectMapper objectMapper;

  public ProposerConfigLoader() {
    this(new JsonProvider().getObjectMapper());
  }

  public ProposerConfigLoader(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ProposerConfig getProposerConfig(final File source) {
    try {
      final ProposerConfig proposerConfig = objectMapper.readValue(source, ProposerConfig.class);
      return proposerConfig;
    } catch (IOException ex) {
      throw new InvalidConfigurationException("Failed to proposer config from File " + source, ex);
    }
  }

  public ProposerConfig getProposerConfig(final URL source) {
    try {
      final ProposerConfig proposerConfig = objectMapper.readValue(source, ProposerConfig.class);
      return proposerConfig;
    } catch (IOException ex) {
      throw new InvalidConfigurationException("Failed to proposer config from URL " + source, ex);
    }
  }
}
