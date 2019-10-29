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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.verifier.OperationVerifier;
import org.ethereum.beacon.core.operations.Transfer;

/**
 * Verifies transfers list.
 *
 * @see Transfer
 */
public class TransferListVerifier extends OperationListVerifier<Transfer> {

  public TransferListVerifier(OperationVerifier<Transfer> operationVerifier, BeaconChainSpec spec) {
    super(
        operationVerifier,
        block -> block.getBody().getTransfers(),
        spec.getConstants().getMaxTransfers());

    addCustomVerifier(
        ((transfers, state) -> {
          List<Transfer> allTransfers =
              StreamSupport.stream(transfers.spliterator(), false).collect(Collectors.toList());
          Set<Transfer> distinctTransfers =
              StreamSupport.stream(transfers.spliterator(), false).collect(Collectors.toSet());

          if (allTransfers.size() > distinctTransfers.size()) {
            return failedResult("two equal transfers have been found");
          }

          return PASSED;
        }));
  }

  @Override
  protected Class<Transfer> getType() {
    return Transfer.class;
  }
}
