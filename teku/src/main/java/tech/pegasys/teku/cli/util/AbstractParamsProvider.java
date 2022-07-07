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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import picocli.CommandLine.Model.OptionSpec;

public abstract class AbstractParamsProvider<V> {

  protected Map<String, String> getAdditionalParam(
      final List<OptionSpec> potentialParams, final Map<String, V> config) {
    final Map<String, String> additionalParams = new HashMap<>();

    config.entrySet().stream()
        .flatMap(this::translateEntry)
        .flatMap(translatedEntry -> mapParam(potentialParams, translatedEntry).stream())
        .forEach(
            mappedParam ->
                additionalParams.merge(
                    mappedParam.getKey(),
                    mappedParam.getValue(),
                    (conflict1, conflict2) -> onConflict(mappedParam, conflict1, conflict2)));
    return additionalParams;
  }

  protected Optional<Entry<String, String>> mapParam(
      final List<OptionSpec> potentialParams, final Entry<String, V> configEntry) {
    return potentialParams.stream()
        .filter(optionSpec -> matchKey(optionSpec, configEntry.getKey()))
        .findFirst()
        .map(optionSpec -> translateToArg(optionSpec, configEntry));
  }

  protected boolean matchKey(final OptionSpec matchedOption, final String envVarTranslatedName) {
    return Arrays.stream(matchedOption.names())
        .anyMatch(name -> name.equalsIgnoreCase(envVarTranslatedName));
  }

  private Stream<Entry<String, V>> translateEntry(final Entry<String, V> configEntry) {
    final Optional<String> maybeTranslated = translateKey(configEntry.getKey());
    if (maybeTranslated.isEmpty()) {
      return Stream.of();
    }

    return Stream.of(
        Map.entry("--" + maybeTranslated.get(), configEntry.getValue()),
        Map.entry("-" + maybeTranslated.get(), configEntry.getValue()));
  }

  protected abstract Entry<String, String> translateToArg(
      OptionSpec matchedOption, Entry<String, V> configEntry);

  protected abstract Optional<String> translateKey(String key);

  protected abstract String onConflict(
      Entry<String, String> mappedParam, String conflict1, String conflict2);
}
