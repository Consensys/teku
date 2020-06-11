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

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import tech.pegasys.teku.util.cli.PicoCliVersionProvider;

@Command(
    name = "generate-and-register",
    description = "Register validators by generating new keys",
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositGenerateCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;

  @Mixin private GenerateParams generateParams;

  public DepositGenerateCommand() {
    this.shutdownFunction =
        System::exit; // required because web3j use non-daemon threads which halts the program
  }

  @VisibleForTesting
  DepositGenerateCommand(
      final Consumer<Integer> shutdownFunction, final GenerateParams generateParams) {
    this.shutdownFunction = shutdownFunction;
    this.generateParams = generateParams;
  }

  @Override
  public void run() {
    final GenerateAction generateAction = generateParams.createGenerateAction();
    for (int i = 0; i < generateParams.getValidatorCount(); i++) {
      generateAction.generateKeys();
    }

    shutdownFunction.accept(0);
  }
}
