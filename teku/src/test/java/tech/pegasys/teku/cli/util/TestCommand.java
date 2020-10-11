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

package tech.pegasys.teku.cli.util;

import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.util.TestCommand.Subcommand;

@Command(subcommands = Subcommand.class)
public interface TestCommand {
  @Option(names = "--count")
  int getCount();

  @Option(names = "--names", split = ",")
  List<String> getNames();

  @Option(names = "--test-enabled", arity = "1")
  boolean isTestEnabled();

  @Command(name = "subcommand")
  interface Subcommand {
    @Option(names = "--subcommand-option-1")
    String getSubcommandOption();
  }
}
