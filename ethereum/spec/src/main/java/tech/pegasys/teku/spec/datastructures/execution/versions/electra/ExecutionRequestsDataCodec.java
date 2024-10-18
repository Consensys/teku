/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;

/*
 Implement the rules for decoding and hashing execution requests according to https://eips.ethereum.org/EIPS/eip-7685
*/
public class ExecutionRequestsDataCodec {

  private static final int EXPECTED_REQUEST_DATA_ELEMENTS = 3;

  private final ExecutionRequestsSchema executionRequestsSchema;

  public ExecutionRequestsDataCodec(final ExecutionRequestsSchema executionRequestsSchema) {
    this.executionRequestsSchema = executionRequestsSchema;
  }

  /**
   * Decodes the execution requests received from the EL.
   *
   * @param executionRequestData list of encoded execution requests from the EL
   * @return an ExecutionRequests object with the requests
   */
  public ExecutionRequests decode(final List<Bytes> executionRequestData) {
    if (executionRequestData.size() != EXPECTED_REQUEST_DATA_ELEMENTS) {
      throw new IllegalArgumentException(
          "Invalid number of execution request data elements: expected "
              + EXPECTED_REQUEST_DATA_ELEMENTS
              + ", received "
              + executionRequestData.size());
    }

    final ExecutionRequestsBuilder executionRequestsBuilder =
        new ExecutionRequestsBuilderElectra(executionRequestsSchema);

    for (int index = 0; index < executionRequestData.size(); index++) {
      // The request type is implicitly defined as the index of the element in executionRequestData
      switch ((byte) index) {
        case DepositRequest.REQUEST_TYPE ->
            executionRequestsBuilder.deposits(
                executionRequestsSchema
                    .getDepositRequestsSchema()
                    .sszDeserialize(executionRequestData.get(index))
                    .asList());
        case WithdrawalRequest.REQUEST_TYPE ->
            executionRequestsBuilder.withdrawals(
                executionRequestsSchema
                    .getWithdrawalRequestsSchema()
                    .sszDeserialize(executionRequestData.get(index))
                    .asList());
        case ConsolidationRequest.REQUEST_TYPE ->
            executionRequestsBuilder.consolidations(
                executionRequestsSchema
                    .getConsolidationRequestsSchema()
                    .sszDeserialize(executionRequestData.get(index))
                    .asList());
        default -> throw new IllegalArgumentException("Invalid execution request type: " + index);
      }
    }

    return executionRequestsBuilder.build();
  }

  /**
   * Encodes the provided ExecutionRequests object to send the requests to the EL for validation.
   *
   * @param executionRequests the execution requests in the BeaconBlock
   * @return list of encoded execution requests
   */
  public List<Bytes> encode(final ExecutionRequests executionRequests) {
    final SszList<DepositRequest> depositRequestsSszList =
        executionRequestsSchema
            .getDepositRequestsSchema()
            .createFromElements(executionRequests.getDeposits());
    final SszList<WithdrawalRequest> withdrawalRequestsSszList =
        executionRequestsSchema
            .getWithdrawalRequestsSchema()
            .createFromElements(executionRequests.getWithdrawals());
    final SszList<ConsolidationRequest> consolidationRequestsSszList =
        executionRequestsSchema
            .getConsolidationRequestsSchema()
            .createFromElements(executionRequests.getConsolidations());

    return List.of(
        depositRequestsSszList.sszSerialize(),
        withdrawalRequestsSszList.sszSerialize(),
        consolidationRequestsSszList.sszSerialize());
  }
}
