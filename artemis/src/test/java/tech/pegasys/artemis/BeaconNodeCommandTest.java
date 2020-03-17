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

package tech.pegasys.artemis;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class BeaconNodeCommandTest {

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.toml");
    final String updatedConfig = Resources.toString(configFile, UTF_8);
    final Path toml = createTempFile("toml", updatedConfig.getBytes(UTF_8));

    final BeaconNodeCommand app = new BeaconNodeCommand();
    final CommandLine cmd = new CommandLine(app);
    cmd.execute("--config", toml.toString());

    final ArtemisConfiguration artemisConfiguration = app.getArtemisConfiguration();

    assertThat(artemisConfiguration.isMetricsEnabled()).isFalse();
    assertThat(artemisConfiguration.getAdvertisedIp()).isEqualTo("127.0.0.1");
    assertThat(artemisConfiguration.getAdvertisedPort()).isEqualTo(9000);
    assertThat(artemisConfiguration.getBeaconRestAPIEnableSwagger()).isFalse();
    assertThat(artemisConfiguration.getBeaconRestAPIPortNumber()).isEqualTo(5051);
    assertThat(artemisConfiguration.getBootnodes()).isEqualTo(Collections.emptyList());
    assertThat(artemisConfiguration.getConstants()).isEqualTo("minimal");
    assertThat(artemisConfiguration.getContractAddr())
        .isEqualTo("0x77f7bED277449F51505a4C54550B074030d989bC");
    assertThat(artemisConfiguration.getDataPath()).isEqualTo(".");
    assertThat(artemisConfiguration.getDepositMode()).isEqualTo("test");
    assertThat(artemisConfiguration.getDiscovery()).isEqualTo("static");

    // hard to test - depends on System.currentTime
    // assertThat(artemisConfiguration.getGenesisTime()).isEqualTo();

    // dev option used for simulation
    // assertThat(artemisConfiguration.getInputFile()).isEqualTo(null);

    assertThat(artemisConfiguration.getInteropOwnedValidatorCount()).isEqualTo(64);
    assertThat(artemisConfiguration.getInteropOwnedValidatorStartIndex()).isEqualTo(0);
    assertThat(artemisConfiguration.getInteropPrivateKey())
        .isEqualTo("0x08021221008166B8EF20C11F3A18F8774BF834173B07F64BAEDA981766896B4A8F53B52EDF");
    assertThat(artemisConfiguration.getMetricCategories())
        .isEqualTo(Arrays.asList("JVM", "PROCESS", "BEACONCHAIN", "EVENTBUS", "NETWORK"));
    assertThat(artemisConfiguration.getMetricsNetworkInterface()).isEqualTo("127.0.0.1");
    assertThat(artemisConfiguration.getMetricsPort()).isEqualTo(8008);
    assertThat(artemisConfiguration.getNetworkID()).isEqualTo(1);
    assertThat(artemisConfiguration.getNetworkInterface()).isEqualTo("0.0.0.0");
    assertThat(artemisConfiguration.getNetworkMode()).isEqualTo("mock");
    assertThat(artemisConfiguration.getNodeUrl()).isEqualTo("http://localhost:8545");
    assertThat(artemisConfiguration.getNumValidators()).isEqualTo(64);
    assertThat(artemisConfiguration.getPort()).isEqualTo(9000);

    // property does not exist
    // assertThat(artemisConfiguration.getProviderType()).isEqualTo("");

    // most likely a dev option
    assertThat(artemisConfiguration.getStartState()).isEqualTo(null);

    assertThat(artemisConfiguration.getTargetPeerCountRangeLowerBound()).isEqualTo(20);
    assertThat(artemisConfiguration.getTargetPeerCountRangeUpperBound()).isEqualTo(30);

    // property does not exist
    // assertThat(artemisConfiguration.getTimer()).isEqualTo(null);

    // most likely a dev option for debugging
    assertThat(artemisConfiguration.getTransitionRecordDir()).isEqualTo(null);

    // application uses mock for testing if this is not set
    assertThat(artemisConfiguration.getValidatorsKeyFile()).isEqualTo(null);

    assertThat(artemisConfiguration.startFromDisk()).isFalse();
  }

  private Path createTempFile(final String filename, final byte[] contents) throws IOException {
    final Path file = Files.createTempFile(filename, "");
    Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }
}
