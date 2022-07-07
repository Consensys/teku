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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

public class EnvironmentVariableParamsProvider extends AbstractParamsProvider<String>
    implements AdditionalParamsProvider {
  private static final String ENV_VAR_PREFIX = "TEKU_";

  private final CommandLine commandLine;
  private final Map<String, String> environment;

  public EnvironmentVariableParamsProvider(
      final CommandLine commandLine, final Map<String, String> environment) {
    this.commandLine = commandLine;
    this.environment = environment;
  }

  @Override
  public Map<String, String> getAdditionalParams(List<OptionSpec> potentialParams) {
    return getAdditionalParam(potentialParams, environment);
  }

  @Override
  protected String onConflict(
      final Entry<String, String> mappedParam, final String conflict1, final String conflict2) {
    throw new ParameterException(
        commandLine,
        String.format(
            "Multiple environment options referring to the same configuration '%s'. Conflicting values: %s and %s",
            mappedParam.getKey().replaceFirst("^-+", ""), conflict1, conflict2));
  }

  @Override
  protected Optional<String> translateKey(String key) {
    if (key.length() <= ENV_VAR_PREFIX.length()) {
      return Optional.empty();
    }
    return Optional.of(key.substring(ENV_VAR_PREFIX.length()).replace('_', '-'));
  }

  @Override
  protected Map.Entry<String, String> translateToArg(
      OptionSpec matchedOption, Map.Entry<String, String> envEntry) {
    return Map.entry(matchedOption.longestName(), envEntry.getValue());
  }
}
