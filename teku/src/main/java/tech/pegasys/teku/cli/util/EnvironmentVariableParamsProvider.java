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

package tech.pegasys.teku.cli.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine.Model.OptionSpec;

public class EnvironmentVariableParamsProvider implements AdditionalParamsProvider {
  private static final String ENV_VAR_PREFIX = "TEKU_";

  private final Map<String, String> environment;

  public EnvironmentVariableParamsProvider(final Map<String, String> environment) {
    this.environment = environment;
  }

  @Override
  public Map<String, String> getAdditionalParams(List<OptionSpec> potentialParams) {
    return environment.entrySet().stream()
        .filter(envEntry -> envEntry.getKey().startsWith(ENV_VAR_PREFIX))
        .map(envEntry -> Map.entry(translateKey(envEntry.getKey()), envEntry.getValue()))
        .map(yamlEntry -> mapParam(potentialParams, yamlEntry))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private String translateKey(final String envVarName) {
    return "--" + envVarName.substring(ENV_VAR_PREFIX.length()).replace('_', '-');
  }

  private Optional<Entry<String, String>> mapParam(
      final List<OptionSpec> potentialParams, final Map.Entry<String, String> envEntry) {
    return potentialParams.stream()
        .filter(optionSpec -> matchKey(optionSpec, envEntry.getKey()))
        .findFirst()
        .map(optionSpec -> translateToArg(optionSpec, envEntry));
  }

  private boolean matchKey(final OptionSpec matchedOption, final String envVarTranslatedName) {
    return Arrays.stream(matchedOption.names())
        .anyMatch(name -> name.equalsIgnoreCase(envVarTranslatedName));
  }

  private Map.Entry<String, String> translateToArg(
      OptionSpec matchedOption, Map.Entry<String, String> envEntry) {
    return Map.entry(matchedOption.longestName(), envEntry.getValue());
  }
}
