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

package tech.pegasys.artemis.cli.options;

import picocli.CommandLine.Option;

public class InteropOptions {

  @Option(
      hidden = true,
      names = {"--Xinterop-genesis-time"},
      paramLabel = "<INTEGER>",
      description = "Time of mocked genesis",
      arity = "1")
  private Integer interopGenesisTime = null;

  @Option(
      hidden = true,
      names = {"--Xinterop-owned-validator-start-index"},
      paramLabel = "<INTEGER>",
      description = "Index of first validator owned by this node",
      arity = "1")
  private int interopOwnerValidatorStartIndex = 0;

  @Option(
      hidden = true,
      names = {"--Xinterop-owned-validator-count"},
      paramLabel = "<INTEGER>",
      description = "Number of validators owned by this node",
      arity = "1")
  private int interopOwnerValidatorCount = 0;

  @Option(
      hidden = true,
      names = {"--Xinterop-number-of-validators"},
      paramLabel = "<INTEGER>",
      description = "Represents the total number of validators in the network")
  private int interopNumberOfValidators = 64;

  @Option(
      hidden = true,
      names = {"--Xinterop-enabled"},
      paramLabel = "<BOOLEAN>",
      fallbackValue = "true",
      description = "Enables developer options for testing",
      arity = "0..1")
  private boolean interopEnabled = false;

  public Integer getInteropGenesisTime() {
    return interopGenesisTime;
  }

  public int getInteropOwnerValidatorStartIndex() {
    return interopOwnerValidatorStartIndex;
  }

  public int getInteropOwnerValidatorCount() {
    return interopOwnerValidatorCount;
  }

  public int getInteropNumberOfValidators() {
    return interopNumberOfValidators;
  }

  public boolean isInteropEnabled() {
    return interopEnabled;
  }
}
