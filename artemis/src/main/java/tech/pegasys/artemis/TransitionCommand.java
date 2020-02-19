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
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.util.cli.VersionProvider;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;

@Command(
    name = "transition",
    description = "Manually run state transitions",
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class TransitionCommand implements Runnable {

  @Command(
      name = "blocks",
      description = "Process blocks on the pre-state to get a post-state",
      mixinStandardHelpOptions = true,
      abbreviateSynopsis = true,
      versionProvider = VersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public void blocks(
      @Mixin InAndOutParams params,
      @Parameters(paramLabel = "block", description = "Files to read blocks from")
          List<String> blocks) {
    processStateTransition(
        params,
        (state, stateTransition) -> {
          if (blocks != null) {
            for (String blockPath : blocks) {
              SignedBeaconBlock block = readBlock(blockPath);
              state = stateTransition.initiate(state, block);
            }
          }
          return state;
        });
  }

  @Command(
      name = "slots",
      description = "Process empty slots on the pre-state to get a post-state",
      mixinStandardHelpOptions = true,
      abbreviateSynopsis = true,
      versionProvider = VersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public void slots(
      @Mixin InAndOutParams params,
      @Option(
              names = {"--delta", "-d"},
              description = "to interpret the slot number as a delta from the pre-state")
          boolean delta,
      @Parameters(paramLabel = "<number>", description = "Number of slots to process")
          long number) {
    processStateTransition(
        params,
        (state, stateTransition) -> {
          UnsignedLong targetSlot = UnsignedLong.valueOf(number);
          if (delta) {
            targetSlot = state.getSlot().plus(targetSlot);
          }
          stateTransition.process_slots(state, targetSlot, false);
          return state;
        });
  }

  private void processStateTransition(
      final InAndOutParams params, final StateTransitionFunction transition) {
    Constants.setConstants(ArtemisConfiguration.fromFile(params.configFile).getConstants());
    try (final InputStream in = selectInputStream(params);
        final OutputStream out = selectOutputStream(params)) {
      final Bytes inData = Bytes.wrap(ByteStreams.toByteArray(in));
      BeaconStateWithCache state =
          BeaconStateWithCache.fromBeaconState(
              SimpleOffsetSerializer.deserialize(inData, BeaconState.class));

      final StateTransition stateTransition = new StateTransition(false);
      try {
        BeaconState result = transition.applyTransition(state, stateTransition);
        out.write(SimpleOffsetSerializer.serialize(result).toArrayUnsafe());
      } catch (final StateTransitionException
          | EpochProcessingException
          | SlotProcessingException e) {
        System.err.println("State transition failed");
        e.printStackTrace();
      }
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

  private SignedBeaconBlock readBlock(final String path) throws IOException {
    final Bytes blockData = Bytes.wrap(Files.readAllBytes(Path.of(path)));
    return SimpleOffsetSerializer.deserialize(blockData, SignedBeaconBlock.class);
  }

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
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

  private interface StateTransitionFunction {
    BeaconState applyTransition(BeaconStateWithCache state, StateTransition stateTransition)
        throws StateTransitionException, EpochProcessingException, SlotProcessingException,
            IOException;
  }
}
