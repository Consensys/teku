/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class MigrateDatabaseCommandTest {

  @Test
  void unstableHelpShouldListLeveldbTreeAsSupportedXtoValue() {
    final StringWriter output = new StringWriter();
    final CommandLine commandLine = new CommandLine(new MigrateDatabaseCommand());
    commandLine.setOut(new PrintWriter(output));

    final int parseResult = commandLine.execute("Xhelp");

    assertThat(parseResult).isZero();
    final String normalizedOutput =
        Pattern.compile("\\s+").matcher(output.toString()).replaceAll(" ").trim();
    assertThat(normalizedOutput)
        .contains("Leveldb types supported are leveldb1, leveldb2, leveldb-tree.");
  }
}
