/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.statetransition.executionproofs.ExecutionProofManager;

public class ExecutionProofsAvailabilityCheckerFactory
    implements AvailabilityCheckerFactory<ExecutionProof> {

  private final ExecutionProofManager executionProofManager;
  private AvailabilityChecker<?> delegate;

  public ExecutionProofsAvailabilityCheckerFactory(
      final ExecutionProofManager executionProofManager) {
    this.executionProofManager = executionProofManager;
  }

  public void setDelegate(final AvailabilityChecker<?> delegate) {
    this.delegate = delegate;
  }

  @Override
  public AvailabilityChecker<ExecutionProof> createAvailabilityChecker(
      final SignedBeaconBlock block) {
    return new ExecutionProofsAvailabilityChecker(executionProofManager, block, delegate);
  }
}
