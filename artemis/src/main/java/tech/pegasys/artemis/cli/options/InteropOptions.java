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

import picocli.CommandLine;

public class InteropOptions {
  public static final String INTEROP_GENESIS_TIME_OPTION_NAME = "--Xinterop-genesis-time";
  public static final String INTEROP_OWNED_VALIDATOR_START_INDEX_OPTION_NAME =
      "--Xinterop-owned-validator-start-index";
  public static final String INTEROP_OWNED_VALIDATOR_COUNT_OPTION_NAME =
      "--Xinterop-owned-validator-count";
  public static final String INTEROP_NUMBER_OF_VALIDATORS_OPTION_NAME =
      "--Xinterop-number-of-validators";
  public static final String INTEROP_ENABLED_OPTION_NAME = "--Xinterop-enabled";

  public static final Integer DEFAULT_X_INTEROP_GENESIS_TIME = null;
  public static final int DEFAULT_X_INTEROP_OWNED_VALIDATOR_START_INDEX = 0;
  public static final int DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT = 0;
  public static final int DEFAULT_X_INTEROP_NUMBER_OF_VALIDATORS = 64;
  public static final boolean DEFAULT_X_INTEROP_ENABLED = false;

  @CommandLine.Option(
      hidden = true,
      names = {INTEROP_GENESIS_TIME_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Time of mocked genesis",
      arity = "1")
  private Integer interopGenesisTime = DEFAULT_X_INTEROP_GENESIS_TIME;

  @CommandLine.Option(
      hidden = true,
      names = {INTEROP_OWNED_VALIDATOR_START_INDEX_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Index of first validator owned by this node",
      arity = "1")
  private int interopOwnerValidatorStartIndex = DEFAULT_X_INTEROP_OWNED_VALIDATOR_START_INDEX;

  @CommandLine.Option(
      hidden = true,
      names = {INTEROP_OWNED_VALIDATOR_COUNT_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Number of validators owned by this node",
      arity = "1")
  private int interopOwnerValidatorCount = DEFAULT_X_INTEROP_OWNED_VALIDATOR_COUNT;

  @CommandLine.Option(
      hidden = true,
      names = {INTEROP_NUMBER_OF_VALIDATORS_OPTION_NAME},
      paramLabel = "<INTEGER>",
      description = "Represents the total number of validators in the network")
  private int interopNumberOfValidators = DEFAULT_X_INTEROP_NUMBER_OF_VALIDATORS;

  @CommandLine.Option(
      hidden = true,
      names = {INTEROP_ENABLED_OPTION_NAME},
      paramLabel = "<BOOLEAN>",
      fallbackValue = "true",
      description = "Enables developer options for testing",
      arity = "0..1")
  private boolean interopEnabled = DEFAULT_X_INTEROP_ENABLED;

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
