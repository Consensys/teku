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

import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.GraffitiConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.util.config.ValidatorPerformanceTrackingMode;

public class ValidatorOptions {

  @CommandLine.Mixin(name = "Validator Keys")
  private ValidatorKeysOptions validatorKeysOptions;

  @Option(
      names = {"--validators-graffiti"},
      converter = GraffitiConverter.class,
      paramLabel = "<GRAFFITI STRING>",
      description =
          "Graffiti to include during block creation (gets converted to bytes and padded to Bytes32).",
      arity = "1")
  private Bytes32 graffiti;

  @Option(
      names = {"--validators-performance-tracking-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable validator performance tracking",
      fallbackValue = "true",
      arity = "0..1",
      hidden = true)
  private Boolean validatorPerformanceTrackingEnabled = null;

  @Option(
      names = {"--validators-performance-tracking-mode"},
      paramLabel = "<TRACKING_MODE>",
      description = "Set strategy for handling performance tracking",
      arity = "1")
  private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode =
      ValidatorPerformanceTrackingMode.ALL;

  @Option(
      names = {"--validators-keystore-locking-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable locking validator keystore files",
      arity = "1")
  private boolean validatorKeystoreLockingEnabled = true;

  @Option(
      names = {"--validators-external-signer-slashing-protection-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enable internal slashing protection for external signers. Default: true",
      fallbackValue = "true",
      arity = "0..1")
  private boolean validatorExternalSignerSlashingProtectionEnabled = true;

  public void configure(TekuConfiguration.Builder builder) {
    if (validatorPerformanceTrackingEnabled != null) {
      if (validatorPerformanceTrackingEnabled) {
        this.validatorPerformanceTrackingMode = ValidatorPerformanceTrackingMode.ALL;
      } else {
        this.validatorPerformanceTrackingMode = ValidatorPerformanceTrackingMode.NONE;
      }
    }

    builder.validator(
        config ->
            config
                .validatorKeystoreLockingEnabled(validatorKeystoreLockingEnabled)
                .validatorPerformanceTrackingMode(validatorPerformanceTrackingMode)
                .validatorExternalSignerSlashingProtectionEnabled(
                    validatorExternalSignerSlashingProtectionEnabled)
                .graffiti(graffiti));
    validatorKeysOptions.configure(builder);
  }
}
