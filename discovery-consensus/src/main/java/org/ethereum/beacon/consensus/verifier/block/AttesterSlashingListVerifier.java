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

import org.ethereum.beacon.consensus.verifier.OperationVerifier;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.spec.SpecConstants;

/**
 * Verifies attester slashing list.
 *
 * @see AttesterSlashing
 */
public class AttesterSlashingListVerifier extends OperationListVerifier<AttesterSlashing> {

  public AttesterSlashingListVerifier(
      OperationVerifier<AttesterSlashing> operationVerifier, SpecConstants specConstants) {
    super(
        operationVerifier,
        block -> block.getBody().getAttesterSlashings(),
        specConstants.getMaxAttesterSlashings());
  }

  @Override
  protected Class<AttesterSlashing> getType() {
    return AttesterSlashing.class;
  }
}
