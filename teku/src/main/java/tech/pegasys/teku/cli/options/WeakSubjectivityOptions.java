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

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;
import tech.pegasys.teku.cli.converter.CheckpointConverter;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WeakSubjectivityOptions {

  @CommandLine.Option(
      names = {"--initial-state"},
      paramLabel = "<STRING>",
      description =
          "The initial state. This value should be a file or URL pointing to an SSZ-encoded finalized checkpoint state.",
      arity = "1")
  private String weakSubjectivityState;

  @CommandLine.Option(
      converter = CheckpointConverter.class,
      names = {"--ws-checkpoint"},
      paramLabel = "<BLOCK_ROOT>:<EPOCH_NUMBER>",
      description = "A recent checkpoint within the weak subjectivity period.",
      arity = "1")
  private Checkpoint weakSubjectivityCheckpoint;

  @CommandLine.Option(
      names = {"--Xws-suppress-errors-until-epoch"},
      paramLabel = "<EPOCH_NUMBER>",
      description =
          "Suppress weak subjectivity finalized checkpoint errors until the supplied epoch is reached.",
      arity = "1",
      hidden = true)
  private UInt64 suppressWSPeriodChecksUntilEpoch = null;

  public TekuConfiguration.Builder configure(TekuConfiguration.Builder builder) {
    return builder.weakSubjectivity(
        wsBuilder -> {
          Optional.ofNullable(weakSubjectivityState)
              .filter(StringUtils::isNotBlank)
              .ifPresent(wsBuilder::weakSubjectivityStateResource);

          if (weakSubjectivityCheckpoint != null) {
            wsBuilder.weakSubjectivityCheckpoint(weakSubjectivityCheckpoint);
          }
          if (suppressWSPeriodChecksUntilEpoch != null) {
            wsBuilder.suppressWSPeriodChecksUntilEpoch(suppressWSPeriodChecksUntilEpoch);
          }
        });
  }
}
