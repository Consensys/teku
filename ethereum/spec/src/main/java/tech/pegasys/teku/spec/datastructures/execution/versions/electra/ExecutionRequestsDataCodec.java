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

import com.google.common.annotations.VisibleForTesting;
import java.util.Comparator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsBuilder;

/*
 Implement the rules for decoding and hashing execution requests according to https://eips.ethereum.org/EIPS/eip-7685
*/
public class ExecutionRequestsDataCodec {

  private final ExecutionRequestsSchema executionRequestsSchema;

  public ExecutionRequestsDataCodec(final ExecutionRequestsSchema executionRequestsSchema) {
    this.executionRequestsSchema = executionRequestsSchema;
  }

  public ExecutionRequests decode(final List<Bytes> executionRequestData) {
    final ExecutionRequestsBuilder executionRequestsBuilder =
        new ExecutionRequestsBuilderElectra(executionRequestsSchema);

    executionRequestData.forEach(
        data -> {
          // First byte of the data is the type of the execution request
          final byte type = data.get(0);
          switch (type) {
            case DepositRequest.REQUEST_TYPE ->
                executionRequestsBuilder.deposits(
                    executionRequestsSchema
                        .getDepositRequestsSchema()
                        .sszDeserialize(data.slice(1))
                        .asList());
            case WithdrawalRequest.REQUEST_TYPE ->
                executionRequestsBuilder.withdrawals(
                    executionRequestsSchema
                        .getWithdrawalRequestsSchema()
                        .sszDeserialize(data.slice(1))
                        .asList());
            case ConsolidationRequest.REQUEST_TYPE ->
                executionRequestsBuilder.consolidations(
                    executionRequestsSchema
                        .getConsolidationRequestsSchema()
                        .sszDeserialize(data.slice(1))
                        .asList());
            default ->
                throw new IllegalArgumentException("Invalid execution request type: " + type);
          }
        });

    return executionRequestsBuilder.build();
  }

  @VisibleForTesting
  List<Bytes> encode(final ExecutionRequests executionRequests) {
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
        Bytes.concatenate(
            Bytes.of(DepositRequest.REQUEST_TYPE), depositRequestsSszList.sszSerialize()),
        Bytes.concatenate(
            Bytes.of(WithdrawalRequest.REQUEST_TYPE), withdrawalRequestsSszList.sszSerialize()),
        Bytes.concatenate(
            Bytes.of(ConsolidationRequest.REQUEST_TYPE),
            consolidationRequestsSszList.sszSerialize()));
  }

  public Bytes32 hash(final ExecutionRequests executionRequests) {
    final Bytes sortedEncodedRequests =
        encode(executionRequests).stream()
            // Encoded requests are sorted by their type (first byte)
            .sorted(Comparator.comparingInt(b -> b.get(0)))
            .reduce(Bytes.EMPTY, Bytes::concatenate);
    return Hash.sha256(sortedEncodedRequests);
  }
}
