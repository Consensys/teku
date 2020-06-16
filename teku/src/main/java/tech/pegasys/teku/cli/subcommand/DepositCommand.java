/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tech.pegasys.teku.cli.deposit.DepositGenerateAndRegisterCommand;
import tech.pegasys.teku.cli.deposit.DepositGenerateCommand;
import tech.pegasys.teku.cli.deposit.DepositRegisterCommand;
import tech.pegasys.teku.util.cli.PicoCliVersionProvider;

@Command(
    name = "validator",
    description = "Register validators by sending deposit transactions to an Ethereum 1 node",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    subcommands = {
      DepositGenerateCommand.class,
      DepositGenerateAndRegisterCommand.class,
      DepositRegisterCommand.class
    },
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositCommand implements Runnable {
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
