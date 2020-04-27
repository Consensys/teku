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

public class OutputOptions {

  public static final String TRANSITION_RECORD_DIRECTORY_OPTION_NAME =
      "--Xtransition-record-directory";

  public static final String DEFAULT_X_TRANSITION_RECORD_DIRECTORY = null;

  @CommandLine.Option(
      hidden = true,
      names = {TRANSITION_RECORD_DIRECTORY_OPTION_NAME},
      paramLabel = "<FILENAME>",
      description = "Directory to record transition pre and post states",
      arity = "1")
  private String transitionRecordDirectory = DEFAULT_X_TRANSITION_RECORD_DIRECTORY;

  public String getTransitionRecordDirectory() {
    return transitionRecordDirectory;
  }
}
