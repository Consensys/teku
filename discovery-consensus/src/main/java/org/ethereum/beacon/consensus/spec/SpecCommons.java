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

package org.ethereum.beacon.consensus.spec;

import java.util.function.Function;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.ValidatorIndex;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** A common part of the spec that is shared by all its components. */
public interface SpecCommons {

  SpecConstants getConstants();

  ObjectHasher<Hash32> getObjectHasher();

  Function<BytesValue, Hash32> getHashFunction();

  boolean isBlsVerify();

  boolean isBlsVerifyProofOfPossession();

  boolean isVerifyDepositProof();

  boolean isComputableGenesisTime();

  default void assertTrue(boolean assertion) {
    if (!assertion) {
      throw new SpecAssertionFailed();
    }
  }

  default void checkIndexRange(BeaconState state, ValidatorIndex index) {
    assertTrue(index.less(state.getValidators().size()));
  }

  class SpecAssertionFailed extends RuntimeException {
    @Override
    public String getMessage() {
      return toString();
    }

    @Override
    public String toString() {
      return String.format(
          "SpecAssertionFailed{%s}",
          getStackTrace().length > 1 ? getStackTrace()[1].toString() : "");
    }
  }
}
