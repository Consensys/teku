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

package tech.pegasys.teku.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.cli.BeaconNodeCommand.StartAction;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.util.config.GlobalConfiguration;

public abstract class AbstractBeaconNodeCommandTest {
  private static final Logger LOG = LogManager.getLogger();
  final StringWriter stringWriter = new StringWriter();
  protected final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  protected final PrintWriter errorWriter = new PrintWriter(stringWriter, true);

  final StartAction startAction = mock(StartAction.class);

  protected BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(outputWriter, errorWriter, Collections.emptyMap(), startAction);

  @TempDir Path dataPath;

  public GlobalConfiguration getResultingGlobalConfiguration() {
    return getResultingTekuConfiguration().global();
  }

  public TekuConfiguration getResultingTekuConfiguration() {
    try {
      final ArgumentCaptor<TekuConfiguration> configCaptor =
          ArgumentCaptor.forClass(TekuConfiguration.class);
      verify(startAction).start(configCaptor.capture(), eq(false));
      assertThat(stringWriter.toString()).isEmpty();

      return configCaptor.getValue();
    } catch (Throwable t) {
      // Ensure we get the errors reported by Teku printed when a test provides invalid input
      // Otherwise it's a nightmare trying to guess why the test is failing
      LOG.error("Failed to parse Teku configuration: " + stringWriter);
      throw t;
    }
  }

  public GlobalConfiguration getGlobalConfigurationFromArguments(String... arguments) {
    return getTekuConfigurationFromArguments(arguments).global();
  }

  public TekuConfiguration getTekuConfigurationFromArguments(String... arguments) {
    beaconNodeCommand.parse(arguments);
    return getResultingTekuConfiguration();
  }

  public GlobalConfiguration getGlobalConfigurationFromFile(String resourceFilename) {
    return getTekuConfigurationFromFile(resourceFilename).global();
  }

  public TekuConfiguration getTekuConfigurationFromFile(String resourceFilename) {
    final String configFile = this.getClass().getResource("/" + resourceFilename).getPath();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile};
    beaconNodeCommand.parse(args);
    return getResultingTekuConfiguration();
  }

  public String getCommandLineOutput() {
    verifyNoInteractions(startAction);
    return new String(stringWriter.getBuffer());
  }
}
