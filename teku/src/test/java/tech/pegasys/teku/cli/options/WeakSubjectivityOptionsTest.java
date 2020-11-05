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

package tech.pegasys.teku.cli.options;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.cli.converter.CheckpointConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WeakSubjectivityOptionsTest extends AbstractBeaconNodeCommandTest {
  @Test
  public void weakSubjectivityCheckpoint_shouldAcceptValue() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    final String checkpointParam = checkpoint.getRoot().toHexString() + ":" + checkpoint.getEpoch();

    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--ws-checkpoint", checkpointParam);
    assertThat(config.weakSubjectivity().getWeakSubjectivityCheckpoint()).contains(checkpoint);
  }

  @Test
  public void weakSubjectivityCheckpoint_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.weakSubjectivity().getWeakSubjectivityCheckpoint()).isEmpty();
  }

  @Test
  public void weakSubjectivityCheckpoint_handleBadValue() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil();
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    final String checkpointParam = checkpoint.getRoot().toHexString() + ":";
    final String[] args = {"--ws-checkpoint", checkpointParam};

    final int result = beaconNodeCommand.parse(args);
    String str = getCommandLineOutput();
    assertThat(str)
        .contains(
            "Invalid value for option '--ws-checkpoint': " + CheckpointConverter.CHECKPOINT_ERROR);
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(result).isGreaterThan(0);
  }

  @Test
  public void suppressWSPeriodChecksUntilEpoch_shouldAcceptValue() {
    final TekuConfiguration config =
        getTekuConfigurationFromArguments("--Xws-suppress-errors-until-epoch", "123");
    assertThat(config.weakSubjectivity().getSuppressWSPeriodChecksUntilEpoch())
        .contains(UInt64.valueOf(123));
  }

  @Test
  public void suppressWSPeriodChecksUntilEpoch_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.weakSubjectivity().getSuppressWSPeriodChecksUntilEpoch()).isEmpty();
  }

  @Test
  public void suppressWSPeriodChecksUntilEpoch_handleBadValue() {
    final String[] args = {"--Xws-suppress-errors-until-epoch", "a:b"};
    final int result = beaconNodeCommand.parse(args);

    String str = getCommandLineOutput();
    assertThat(str).contains("Invalid value for option '--Xws-suppress-errors-until-epoch'");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(result).isGreaterThan(0);
  }

  @Test
  public void weakSubjectivityStateAndBlock_shouldAcceptValue() {
    final String state = "state.ssz";
    final String block = "block.ssz";
    final TekuConfiguration config =
        getTekuConfigurationFromArguments(
            "--Xws-initial-state", state, "--Xws-initial-block", block);
    assertThat(config.weakSubjectivity().getWeakSubjectivityStateResource()).contains(state);
    assertThat(config.weakSubjectivity().getWeakSubjectivityBlockResource()).contains(block);
  }

  @Test
  public void weakSubjectivityState_withoutBlock() {
    final String[] args = {"--Xws-initial-state", "state.ssz"};
    final int result = beaconNodeCommand.parse(args);

    String str = getCommandLineOutput();
    assertThat(str)
        .contains("Error: --Xws-initial-block and --Xws-initial-state must be specified together");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(result).isGreaterThan(0);
  }

  @Test
  public void weakSubjectivityState_withoutState() {
    final String[] args = {"--Xws-initial-block", "block.ssz"};
    final int result = beaconNodeCommand.parse(args);

    String str = getCommandLineOutput();
    assertThat(str)
        .contains("Error: --Xws-initial-block and --Xws-initial-state must be specified together");
    assertThat(str).contains("To display full help:");
    assertThat(str).contains("--help");
    assertThat(result).isGreaterThan(0);
  }

  @Test
  public void weakSubjectivityState_shouldDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.weakSubjectivity().getWeakSubjectivityStateResource()).isEmpty();
  }

  @Test
  public void weakSubjectivityState_fromConfig() {
    final TekuConfiguration config =
        getTekuConfigurationFromFile("weakSubjectivityOptions_config.yaml");
    assertThat(config.weakSubjectivity().getWeakSubjectivityStateResource()).contains("state.ssz");
    assertThat(config.weakSubjectivity().getWeakSubjectivityBlockResource()).contains("block.ssz");
  }
}
