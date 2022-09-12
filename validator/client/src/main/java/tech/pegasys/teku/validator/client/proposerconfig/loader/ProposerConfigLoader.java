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

package tech.pegasys.teku.validator.client.proposerconfig.loader;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
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

  public ProposerConfig getProposerConfig(final URL source) {
    try {
      return objectMapper.readValue(source, ProposerConfig.class);

    } catch (ValueInstantiationException ex) {
      final String error =
          getErrorMessage(
              source, ExceptionUtil.getRootCauseMessage(ex), Optional.of(ex.getLocation()));
      throw new InvalidConfigurationException(error, ex);
    } catch (JsonMappingException ex) {
      final String error =
          getErrorMessage(source, ex.getOriginalMessage(), Optional.ofNullable(ex.getLocation()));
      throw new InvalidConfigurationException(error, ex);
    } catch (IOException ex) {
      final String error =
          getErrorMessage(source, ExceptionUtil.getMessageOrSimpleName(ex), Optional.empty());
      throw new InvalidConfigurationException(error, ex);
    }
  }

  private static String getErrorMessage(
      final URL source, final String exceptionMessage, Optional<JsonLocation> maybeLocation) {
    return String.format(
        "Failed to load proposer config from '%s' - %s%s",
        UrlSanitizer.sanitizePotentialUrl(source.toString()),
        exceptionMessage,
        maybeLocation.map(jsonLocation -> " at " + jsonLocation.offsetDescription()).orElse(""));
  }
}
