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

package tech.pegasys.teku.cli.options;

import picocli.CommandLine.Option;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorProposerOptions {
  @Option(
      names = {"--validators-proposer-default-fee-recipient"},
      paramLabel = "<ADDRESS>",
      description =
          "Default fee recipient sent to the execution engine, which could use it as fee recipient when producing a new execution block.",
      arity = "0..1")
  private String proposerDefaultFeeRecipient = null;

  @Option(
      names = {"--validators-proposer-config"},
      paramLabel = "<STRING>",
      description = "remote URL or local file path to load proposer configuration from",
      arity = "0..1")
  private String proposerConfig = null;

  @Option(
      names = {"--validators-proposer-config-refresh-enabled"},
      paramLabel = "<BOOLEAN>",
      description =
          "Enable the proposer configuration reload on every proposer preparation (once per epoch)",
      arity = "0..1",
      fallbackValue = "true")
  private boolean proposerConfigRefreshEnabled =
      ValidatorConfig.DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED;

  public void configure(TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .proposerDefaultFeeRecipient(proposerDefaultFeeRecipient)
                .proposerConfigSource(proposerConfig)
                .refreshProposerConfigFromSource(proposerConfigRefreshEnabled));
  }
}
