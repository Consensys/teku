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

package org.ethereum.beacon.core.spec;

/**
 * Honest validator parameters.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/59b301f7af7b91925083bcf68d460a44bbc8254e/specs/validator/0_beacon-chain-validator.md#misc">Misc</a>
 *     in Honest Validator.
 */
public interface HonestValidatorParameters {

  long ETH1_FOLLOW_DISTANCE = 1L << 10; // 1024 blocks, ~4 hours

  default long getEth1FollowDistance() {
    return ETH1_FOLLOW_DISTANCE;
  }
}
