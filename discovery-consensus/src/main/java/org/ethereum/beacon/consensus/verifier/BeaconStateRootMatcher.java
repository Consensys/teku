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

package org.ethereum.beacon.consensus.verifier;

import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;

/**
 * Matches hash of the state to {@link BeaconBlock#stateRoot} of given block.
 *
 * <p>Fails if given state doesn't match to the state of the block.
 */
public class BeaconStateRootMatcher implements BeaconStateVerifier {

  private final BeaconChainSpec spec;

  public BeaconStateRootMatcher(BeaconChainSpec spec) {
    this.spec = spec;
  }

  @Override
  public VerificationResult verify(BeaconState state, BeaconBlock block) {
    try {
      spec.verify_block_state_root(state, block);
      return VerificationResult.PASSED;
    } catch (Exception e) {
      return VerificationResult.failedResult(
          "State root doesn't match, expected %s but got %s",
          block.getStateRoot(), spec.hash_tree_root(state));
    }
  }
}
