/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.cli.options;

import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.infrastructure.logging.LoggingConfig;

public class ValidatorClientLoggingOptions extends LoggingOptions {
  @Option(
      names = {"--log-include-validator-duties-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Whether events are logged when validators perform duties",
      fallbackValue = "true",
      arity = "0..1")
  private boolean includeValidatorDutiesEnabled =
      LoggingConfig.DEFAULT_INCLUDE_VALIDATOR_DUTIES_ENABLED;

  @Option(
      names = {"--log-validator-duties-verbose-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enable verbose logging of validator duties schedule. Not recommended for several validators",
      fallbackValue = "true",
      arity = "0..1")
  private boolean validatorDutiesVerboseEnabled =
      LoggingConfig.DEFAULT_LOG_VALIDATOR_DUTIES_VERBOSE;

  @Override
  public LoggingConfig applyLoggingConfiguration(
      String dataDirectory, String defaultLogFileNamePrefix) {
    final LoggingConfig.LoggingConfigBuilder loggingBuilder =
        LoggingConfig.builder()
            .validatorVerboseDuties(validatorDutiesVerboseEnabled)
            .includeValidatorDutiesEnabled(includeValidatorDutiesEnabled);
    return super.applyLoggingConfiguration(dataDirectory, defaultLogFileNamePrefix, loggingBuilder);
  }
}
