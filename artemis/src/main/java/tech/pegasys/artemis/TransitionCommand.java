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

package tech.pegasys.artemis;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

@Command(
    name = "transition",
    description = "Run state transitions",
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Artemis is licensed under the Apache License 2.0")
public class TransitionCommand {

  @Command(
      name = "blocks",
      description = "Process blocks on the pre-state to get a post-state",
      mixinStandardHelpOptions = true,
      abbreviateSynopsis = true,
      versionProvider = VersionProvider.class,
      header = "Usage:",
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Artemis is licensed under the Apache License 2.0")
  public void blocks(@Mixin InAndOutParams params, @Parameters List<String> blocks) {
    initConstants(params);
    try (final InputStream in = selectInputStream(params);
        final OutputStream out = selectOutputStream(params)) {
      final Bytes inData = Bytes.wrap(ByteStreams.toByteArray(in));
      BeaconStateWithCache state =
          BeaconStateWithCache.fromBeaconState(
              SimpleOffsetSerializer.deserialize(inData, BeaconState.class));

      final StateTransition stateTransition = new StateTransition(true);
      if (blocks != null) {
        for (String blockPath : blocks) {
          BeaconBlock block = readBlock(blockPath);
          try {
            state = stateTransition.initiate(state, block);
          } catch (StateTransitionException e) {
            System.err.println("State transition failed on block " + blockPath);
            e.printStackTrace();
            return;
          }
        }
      }

      out.write(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());
    } catch (final IOException e) {
      System.err.println("I/O error: " + e.toString());
    }
  }

  private OutputStream selectOutputStream(@Mixin final InAndOutParams params) throws IOException {
    return params.post != null ? Files.newOutputStream(Path.of(params.post)) : System.out;
  }

  private InputStream selectInputStream(@Mixin final InAndOutParams params) throws IOException {
    if (params.pre != null) {
      final Path inputPath = Path.of(params.pre);
      return Files.newInputStream(inputPath);
    } else {
      return System.in;
    }
  }

  private BeaconBlock readBlock(final String path) throws IOException {
    final Bytes blockData = Bytes.wrap(Files.readAllBytes(Path.of(path)));
    return SimpleOffsetSerializer.deserialize(blockData, BeaconBlock.class);
  }

  private void initConstants(final InAndOutParams params) {
    Constants.init(ArtemisConfiguration.fromFile(params.configFile));
  }

  public static class InAndOutParams {

    @Option(
        names = {"--post", "-o"},
        description = "Post (Output) path. If none is specified, output is written to STDOUT")
    private String post;

    @Option(
        names = {"--pre", "-i"},
        description = "Pre (Input) path. If none is specified, input is read from STDIN")
    private String pre;

    @Option(
        names = {"-c", "--config"},
        paramLabel = "<FILENAME>",
        description = "Path/filename of the config file")
    private String configFile = "./config/config.toml";

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("post", post).add("pre", pre).toString();
    }
  }
}
