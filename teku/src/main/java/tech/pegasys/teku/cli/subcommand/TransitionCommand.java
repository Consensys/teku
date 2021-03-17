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

package tech.pegasys.teku.cli.subcommand;

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import com.google.common.base.MoreObjects;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.util.config.Constants;

@Command(
    name = "transition",
    description = "Manually run state transitions",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
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
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int blocks(
      @Mixin InAndOutParams params,
      @Parameters(paramLabel = "block", description = "Files to read blocks from")
          List<String> blocks) {
    return processStateTransition(
        params,
        (spec, state) -> {
          if (blocks != null) {
            for (String blockPath : blocks) {
              SignedBeaconBlock block = readBlock(spec, blockPath);
              state = spec.initiateStateTransition(state, block);
            }
          }
          return state;
        });
  }

  @Command(
      name = "slots",
      description = "Process empty slots on the pre-state to get a post-state",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int slots(
      @Mixin InAndOutParams params,
      @Option(
              names = {"--delta", "-d"},
              description = "to interpret the slot number as a delta from the pre-state")
          boolean delta,
      @Parameters(paramLabel = "<number>", description = "Number of slots to process")
          long number) {
    return processStateTransition(
        params,
        (specProvider, state) -> {
          UInt64 targetSlot = UInt64.valueOf(number);
          if (delta) {
            targetSlot = state.getSlot().plus(targetSlot);
          }
          return specProvider.processSlots(state, targetSlot);
        });
  }

  private int processStateTransition(
      final InAndOutParams params, final StateTransitionFunction transition) {
    final Spec spec = params.eth2NetworkOptions.getNetworkConfiguration().getSpec();
    Constants.setConstants(params.eth2NetworkOptions.getNetworkConfiguration().getConstants());
    try (final InputStream in = selectInputStream(params);
        final OutputStream out = selectOutputStream(params)) {
      final Bytes inData = Bytes.wrap(ByteStreams.toByteArray(in));
      BeaconState state = readState(spec, inData);

      try {
        BeaconState result = transition.applyTransition(spec, state);
        out.write(result.sszSerialize().toArrayUnsafe());
        return 0;
      } catch (final StateTransitionException
          | EpochProcessingException
          | SlotProcessingException e) {
        SUB_COMMAND_LOG.error("State transition failed", e);
        return 1;
      }
    } catch (final SSZException e) {
      SUB_COMMAND_LOG.error(e.getMessage());
      return 1;
    } catch (final IOException e) {
      SUB_COMMAND_LOG.error("I/O error: " + e.toString());
      return 1;
    } catch (final Throwable t) {
      t.printStackTrace();
      return 2;
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

  private BeaconState readState(final Spec spec, final Bytes inData) {
    try {
      return spec.deserializeBeaconState(inData);
    } catch (final IllegalArgumentException e) {
      throw new SSZException("Failed to parse SSZ (pre state): " + e.getMessage(), e);
    }
  }

  private SignedBeaconBlock readBlock(final Spec spec, final String path) throws IOException {
    final Bytes blockData = Bytes.wrap(Files.readAllBytes(Path.of(path)));
    try {
      return spec.deserializeSignedBeaconBlock(blockData);
    } catch (final IllegalArgumentException e) {
      throw new SSZException("Failed to parse SSZ (" + path + "): " + e.getMessage(), e);
    }
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

    @Mixin private Eth2NetworkOptions eth2NetworkOptions;

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("post", post).add("pre", pre).toString();
    }
  }

  private interface StateTransitionFunction {
    BeaconState applyTransition(final Spec spec, BeaconState state)
        throws StateTransitionException, EpochProcessingException, SlotProcessingException,
            IOException;
  }
}
