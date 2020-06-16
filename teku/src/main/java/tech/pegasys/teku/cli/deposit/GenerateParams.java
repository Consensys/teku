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

package tech.pegasys.teku.cli.deposit;

import java.util.function.Function;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;
import tech.pegasys.teku.cli.deposit.GenerateAction.ValidatorPasswordOptions;
import tech.pegasys.teku.cli.deposit.GenerateAction.WithdrawalPasswordOptions;

public class GenerateParams {

  @Spec private CommandSpec spec;

  @Option(
      names = {"--number-of-validators"},
      paramLabel = "<NUMBER>",
      description = "The number of validators to create keys for and register",
      converter = PositiveIntegerTypeConverter.class,
      defaultValue = "1")
  private int validatorCount = 1;

  @Option(
      names = {"--keys-output-path"},
      paramLabel = "<FILE|DIR>",
      description =
          "Path to output file for unencrypted keys or output directory for encrypted keystore files. If not set, unencrypted keys will be written on standard out and encrypted keystores will be created in current directory")
  private String outputPath;

  @Option(
      names = {"--encrypted-keystore-enabled"},
      defaultValue = "true",
      paramLabel = "<true|false>",
      description = "Create encrypted keystores for validator and withdrawal keys. (Default: true)",
      arity = "1")
  private boolean encryptKeys = true;

  @ArgGroup(heading = "Non-interactive password options for validator keystores:%n")
  private ValidatorPasswordOptions validatorPasswordOptions;

  @ArgGroup(heading = "Non-interactive password options for withdrawal keystores:%n")
  private WithdrawalPasswordOptions withdrawalPasswordOptions;

  private final Function<String, String> envSupplier;
  private final ConsoleAdapter consoleAdapter;

  public GenerateParams() {
    this.envSupplier = System::getenv;
    this.consoleAdapter = new ConsoleAdapter();
  }

  public GenerateParams(
      final CommandSpec spec,
      final Function<String, String> envSupplier,
      final ConsoleAdapter consoleAdapter) {
    this.spec = spec;
    this.envSupplier = envSupplier;
    this.consoleAdapter = consoleAdapter;
  }

  public GenerateAction createGenerateAction(final boolean displayConfirmation) {
    return new GenerateAction(
        validatorCount,
        outputPath,
        encryptKeys,
        validatorPasswordOptions,
        withdrawalPasswordOptions,
        displayConfirmation,
        consoleAdapter,
        spec,
        envSupplier);
  }

  public int getValidatorCount() {
    return validatorCount;
  }

  private static class PositiveIntegerTypeConverter implements ITypeConverter<Integer> {
    @Override
    public Integer convert(final String value) throws TypeConversionException {
      try {
        final int parsedValue = Integer.parseInt(value);
        if (parsedValue <= 0) {
          throw new TypeConversionException("Must be a positive number");
        }
        return parsedValue;
      } catch (final NumberFormatException e) {
        throw new TypeConversionException(
            "Invalid format: must be a numeric value but was '" + value + "'");
      }
    }
  }
}
