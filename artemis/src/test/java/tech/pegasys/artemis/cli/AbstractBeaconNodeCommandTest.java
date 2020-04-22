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

package tech.pegasys.artemis.cli;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.cli.BeaconNodeCommand.CONFIG_FILE_OPTION_NAME;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class AbstractBeaconNodeCommandTest {
  protected final PrintWriter outputWriter = new PrintWriter(new StringWriter(), true);
  protected final PrintWriter errorWriter = new PrintWriter(new StringWriter(), true);

  @SuppressWarnings("unchecked")
  final Consumer<ArtemisConfiguration> startAction = mock(Consumer.class);

  protected BeaconNodeCommand beaconNodeCommand =
      new BeaconNodeCommand(outputWriter, errorWriter, Collections.emptyMap(), startAction);

  @TempDir Path dataPath;

  public ArtemisConfiguration getResultingArtemisConfiguration() {
    final ArgumentCaptor<ArtemisConfiguration> configCaptor =
        ArgumentCaptor.forClass(ArtemisConfiguration.class);
    verify(startAction).accept(configCaptor.capture());

    return configCaptor.getValue();
  }

  public ArtemisConfiguration getArtemisConfigurationFromArguments(List<String> arguments) {
    beaconNodeCommand.parse(arguments.toArray(String[]::new));
    return getResultingArtemisConfiguration();
  }

  public ArtemisConfiguration getArtemisConfigurationFromFile(String resourceFilename) {
    final String configFile = this.getClass().getResource("/" + resourceFilename).getPath();
    final String[] args = {CONFIG_FILE_OPTION_NAME, configFile};
    beaconNodeCommand.parse(args);
    return getResultingArtemisConfiguration();
  }
}
