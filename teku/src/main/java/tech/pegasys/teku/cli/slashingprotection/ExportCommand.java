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

package tech.pegasys.teku.cli.slashingprotection;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.util.Strings;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.ValidatorClientDataOptions;
import tech.pegasys.teku.data.SlashingProtectionExporter;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.ValidatorClientService;

@CommandLine.Command(
    name = "export",
    description = "Export slashing protection database in minimal format.",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class ExportCommand implements Runnable {
  public static final SubCommandLogger SUB_COMMAND_LOG = new SubCommandLogger();

  @CommandLine.Mixin(name = "Data")
  private ValidatorClientDataOptions dataOptions;

  @CommandLine.Option(
      names = {"--to"},
      paramLabel = "<FILENAME>",
      description = "The file to export the slashing protection database to.",
      required = true,
      arity = "1")
  private String toFileName = Strings.EMPTY;

  @Override
  public void run() {

    final Path slashProtectionPath = getSlashingProtectionPath(dataOptions);
    verifySlashingProtectionPathExists(slashProtectionPath);

    SlashingProtectionExporter slashingProtectionExporter =
        new SlashingProtectionExporter(SUB_COMMAND_LOG);

    SUB_COMMAND_LOG.display("Reading slashing protection data from: " + slashProtectionPath);
    slashingProtectionExporter.initialise(slashProtectionPath);

    try {
      SUB_COMMAND_LOG.display("Writing slashing protection data to: " + toFileName);
      slashingProtectionExporter.saveToFile(toFileName);
    } catch (IOException e) {
      SUB_COMMAND_LOG.exit(1, "Failed to export slashing protection data.", e);
    }
  }

  private void verifySlashingProtectionPathExists(final Path slashProtectionPath) {
    if (!slashProtectionPath.toFile().exists() || !slashProtectionPath.toFile().isDirectory()) {
      SUB_COMMAND_LOG.exit(
          1,
          "Unable to locate the path containing slashing protection data. Expected "
              + slashProtectionPath.toString()
              + " to be a directory containing slashing protection yml files.");
    }
  }

  private Path getSlashingProtectionPath(final ValidatorClientDataOptions dataOptions) {
    final DataDirLayout dataDirLayout = DataDirLayout.createFrom(dataOptions.getDataConfig());
    return ValidatorClientService.getSlashingProtectionPath(dataDirLayout);
  }
}
