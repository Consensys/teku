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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.validator.api.ValidatorConfig.DEFAULT_VALIDATOR_BLINDED_BLOCKS_ENABLED;

import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.UInt64Converter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class ValidatorProposerOptions {
  @Option(
      names = {"--validators-proposer-default-fee-recipient"},
      paramLabel = "<ADDRESS>",
      description =
          "Default fee recipient sent to the execution engine, which could use it as fee recipient when producing a new execution block.",
      arity = "1")
  private String proposerDefaultFeeRecipient = null;

  @Option(
      names = {"--validators-proposer-config"},
      paramLabel = "<STRING>",
      description = "remote URL or local file path to load proposer configuration from",
      arity = "1")
  private String proposerConfig = null;

  @Option(
      names = {"--validators-proposer-config-refresh-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Enable the proposer configuration reload on every proposer preparation (once per epoch)",
      arity = "0..1",
      fallbackValue = "true")
  private boolean proposerConfigRefreshEnabled =
      ValidatorConfig.DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED;

  @Option(
      names = {"--validators-builder-registration-default-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Enable validators registration to builder infrastructure.",
      arity = "0..1",
      fallbackValue = "true")
  private boolean builderRegistrationDefaultEnabled =
      ValidatorConfig.DEFAULT_BUILDER_REGISTRATION_DEFAULT_ENABLED;

  @Option(
      names = {"--Xvalidators-builder-registration-default-gas-limit"},
      paramLabel = "<uint64>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Change the default gas limit used for the validators registration.",
      arity = "1",
      hidden = true,
      converter = UInt64Converter.class)
  private UInt64 builderRegistrationDefaultGasLimit =
      ValidatorConfig.DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT;

  @Option(
      names = {"--Xvalidators-builder-registration-sending-batch-size"},
      paramLabel = "<INTEGER>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Change the default batch size for sending validator registrations to the Beacon Node.",
      arity = "1",
      hidden = true)
  private int builderRegistrationSendingBatchSize =
      ValidatorConfig.DEFAULT_VALIDATOR_REGISTRATION_SENDING_BATCH_SIZE;

  @Option(
      names = {"--Xvalidators-builder-registration-timestamp-override"},
      paramLabel = "<uint64>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Set a constant timestamp in Unix format to be used in validator registrations against builder infrastructure.",
      arity = "1",
      hidden = true)
  private UInt64 builderRegistrationTimestampOverride = null;

  @Option(
      names = {"--validators-proposer-blinded-blocks-enabled"},
      paramLabel = "<BOOLEAN>",
      showDefaultValue = Visibility.ALWAYS,
      description = "Use blinded blocks when in block production duties",
      fallbackValue = "true",
      arity = "0..1")
  private boolean blindedBlocksEnabled = DEFAULT_VALIDATOR_BLINDED_BLOCKS_ENABLED;

  public void configure(TekuConfiguration.Builder builder) {
    builder.validator(
        config ->
            config
                .proposerDefaultFeeRecipient(proposerDefaultFeeRecipient)
                .proposerConfigSource(proposerConfig)
                .refreshProposerConfigFromSource(proposerConfigRefreshEnabled)
                .builderRegistrationDefaultEnabled(builderRegistrationDefaultEnabled)
                .blindedBeaconBlocksEnabled(blindedBlocksEnabled)
                .builderRegistrationDefaultGasLimit(builderRegistrationDefaultGasLimit)
                .builderRegistrationSendingBatchSize(builderRegistrationSendingBatchSize)
                .builderRegistrationTimestampOverride(builderRegistrationTimestampOverride));
  }
}
