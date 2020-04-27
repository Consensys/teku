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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.util.config.TekuConfiguration;

public abstract class AbstractBeaconNodeCommandTest {
  final StringWriter stringWriter = new StringWriter();
  protected final PrintWriter outputWriter = new PrintWriter(stringWriter, true);
  protected final PrintWriter errorWriter = new PrintWriter(stringWriter, true);

  @SuppressWarnings("unchecked")
  final Consumer<TekuConfiguration> startAction = mock(Consumer.class);

  protected BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(outputWriter, errorWriter, Collections.emptyMap(), startAction);

  @TempDir Path dataPath;

  public TekuConfiguration getResultingTekuConfiguration() {
    final ArgumentCaptor<TekuConfiguration> configCaptor =
        ArgumentCaptor.forClass(TekuConfiguration.class);
    verify(startAction).accept(configCaptor.capture());

    return configCaptor.getValue();
  }

  public TekuConfiguration getTekuConfigurationFromArguments(String... arguments) {
    beaconNodeCommand.parse(arguments);
    return getResultingTekuConfiguration();
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
