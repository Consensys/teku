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

import org.ethereum.beacon.core.BeaconState;

/**
 * Interface to verify various beacon chain operations.
 *
 * @param <T> an operation type.
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#operations">Operations</a>
 *     in the spec.
 */
public interface OperationVerifier<T> {

  /**
   * Runs operation verifications.
   *
   * @param operation an operation to verify.
   * @param state a state produced by per-slot processing which {@code slot} is equal to the slot of
   *     the block which operation does belong to.
   * @return result of the verifications.
   * @see <a
   *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#per-slot-processing">Per-slot
   *     processing</a> in the spec.
   */
  VerificationResult verify(T operation, BeaconState state);
}
