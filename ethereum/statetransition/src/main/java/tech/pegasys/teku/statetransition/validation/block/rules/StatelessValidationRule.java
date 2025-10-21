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

package tech.pegasys.teku.statetransition.validation.block.rules;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface StatelessValidationRule {
  /**
   * Performs a single validation check on a block.
   *
   * @param block The block being validated.
   * @return An Optional containing an InternalValidationResult if the rule fails. Returns
   *     Optional.empty() if the rule passes.
   */
  Optional<InternalValidationResult> validate(SignedBeaconBlock block);
}
