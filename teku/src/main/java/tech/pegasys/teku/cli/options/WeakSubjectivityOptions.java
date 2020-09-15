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

import picocli.CommandLine;
import tech.pegasys.teku.config.TekuConfigurationBuilder;

public class WeakSubjectivityOptions {
  // TODO(#2779) - Make this option public when we're ready
  @CommandLine.Option(
      names = {"--Xweak-subjectivity-checkpoint"},
      paramLabel = "<BLOCK_ROOT>:<EPOCH_NUMBER>",
      description = "A recent checkpoint within the weak subjectivity period.",
      arity = "1",
      hidden = true)
  private String weakSubjectivityCheckpoint = "";

  public TekuConfigurationBuilder configure(TekuConfigurationBuilder builder) {
    return builder.weakSubjectivity(
        wsBuilder -> {
          if (!weakSubjectivityCheckpoint.isBlank()) {
            wsBuilder.setWeakSubjectivityCheckpoint(weakSubjectivityCheckpoint);
          }
        });
  }
}
