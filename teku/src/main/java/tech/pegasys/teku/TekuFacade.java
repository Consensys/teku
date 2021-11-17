/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku;

import java.util.Optional;
import tech.pegasys.teku.config.TekuConfiguration;

/**
 * The main entry point to start Teku node
 *
 * <p>CAUTION: this API is unstable and primarily intended for debugging and testing purposes this
 * API might be changed in any version in backward incompatible way
 */
public interface TekuFacade {

  /**
   * Starts Teku node from CLI args
   *
   * @return Either {@link NodeFacade} or {@link Optional#empty()} if arguments are not supposed to
   *     start a Node (e.g. <code>--help</code> command)
   * @throws RuntimeException if invalid args supplied or an internal error while starting a Node
   */
  static Optional<? extends NodeFacade> startFromCLIArgs(String[] cliArgs) {
    return Teku.startFromCLIArgs(cliArgs);
  }

  static BeaconNodeFacade startBeaconNode(TekuConfiguration config) {
    return Teku.startBeaconNode(config);
  }

  static ValidatorNodeFacade startValidatorNode(TekuConfiguration config) {
    return Teku.startValidatorNode(config);
  }
}
