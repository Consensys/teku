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

package org.ethereum.beacon.consensus.verifier.block;

import static org.ethereum.beacon.consensus.verifier.VerificationResult.PASSED;
import static org.ethereum.beacon.consensus.verifier.VerificationResult.failedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.ethereum.beacon.consensus.verifier.BeaconBlockVerifier;
import org.ethereum.beacon.consensus.verifier.OperationVerifier;
import org.ethereum.beacon.consensus.verifier.VerificationResult;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;
import tech.pegasys.artemis.util.collections.ReadList;

/**
 * An abstract class for a family of beacon chain operation verifiers.
 *
 * <p>Accepts an instance of {@link OperationVerifier}, operation list extractor and max number of
 * operations that is allowed to be included in the list.
 *
 * <p>Its {@link #verify(BeaconBlock, BeaconState)} method does following things:
 *
 * <ul>
 *   <li>Extracts a list of operations with {@link #operationListExtractor}.
 *   <li>Verifies that the list has at most {@link #maxOperationsInList} items.
 *   <li>Verifies each operation in the list by applying {@link #operationVerifier} to it.
 * </ul>
 *
 * @param <T> beacon chain operation type.
 * @see OperationVerifier
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md#operations">Operations</a>
 *     in the spec.
 */
public abstract class OperationListVerifier<T> implements BeaconBlockVerifier {

  private OperationVerifier<T> operationVerifier;
  private Function<BeaconBlock, Iterable<T>> operationListExtractor;
  private int maxOperationsInList;
  private List<BiFunction<Iterable<T>, BeaconState, VerificationResult>> customVerifiers;

  protected OperationListVerifier(
      OperationVerifier<T> operationVerifier,
      Function<BeaconBlock, Iterable<T>> operationListExtractor,
      int maxOperationsInList) {
    this.operationVerifier = operationVerifier;
    this.operationListExtractor = operationListExtractor;
    this.maxOperationsInList = maxOperationsInList;
    this.customVerifiers = new ArrayList<>();
  }

  @Override
  public VerificationResult verify(BeaconBlock block, BeaconState state) {
    Iterable<T> operations = operationListExtractor.apply(block);

    if (ReadList.sizeOf(operations) > maxOperationsInList) {
      return VerificationResult.failedResult(
          "%s max number exceeded, should be at most %d but got %d",
          getType().getSimpleName(), maxOperationsInList, ReadList.sizeOf(operations));
    }

    for (BiFunction<Iterable<T>, BeaconState, VerificationResult> verifier : customVerifiers) {
      VerificationResult result = verifier.apply(operations, state);
      if (result != PASSED) {
        return failedResult(
            "%s list verification failed: %s", getType().getSimpleName(), result.getMessage());
      }
    }

    int i = 0;
    for (T operation : operations) {
      VerificationResult result = operationVerifier.verify(operation, state);
      if (result != PASSED) {
        return failedResult("%s #%d: %s", getType().getSimpleName(), i, result.getMessage());
      }
      i += 1;
    }

    return PASSED;
  }

  protected OperationListVerifier<T> addCustomVerifier(
      BiFunction<Iterable<T>, BeaconState, VerificationResult> verifier) {
    this.customVerifiers.add(verifier);
    return this;
  }

  protected abstract Class<T> getType();
}
