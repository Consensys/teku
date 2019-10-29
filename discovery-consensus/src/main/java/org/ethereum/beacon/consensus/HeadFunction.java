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

package org.ethereum.beacon.consensus;

import java.util.Optional;
import java.util.function.Function;
import org.ethereum.beacon.consensus.spec.ForkChoice.LatestMessage;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.types.ValidatorIndex;

/** Head function updates head */
public interface HeadFunction {

  /**
   * Updates actual head on chain and returns it
   *
   * @param latestMessageStorage Storage "ValidatorIndex : LatestMessage" at latest state
   * @return head block
   */
  BeaconBlock getHead(Function<ValidatorIndex, Optional<LatestMessage>> latestMessageStorage);
}
