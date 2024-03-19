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

package tech.pegasys.teku.ethereum.executionclient.methods;

import static tech.pegasys.teku.ethereum.executionclient.methods.EngineNewInclusionListV1.INCLUSION_LIST_CONFIGURATION;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.GetInclusionListResponse;
import tech.pegasys.teku.spec.datastructures.execution.TransactionSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class EngineGetInclusionListV1
    extends AbstractEngineJsonRpcMethod<GetInclusionListResponse> {
  private static final Logger LOG = LogManager.getLogger();

  private final SpecVersion specVersion;

  public EngineGetInclusionListV1(
      final ExecutionEngineClient executionEngineClient, final SpecVersion specVersion) {
    super(executionEngineClient);
    this.specVersion = specVersion;
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_GET_INCLUSION_LIST.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<GetInclusionListResponse> execute(final JsonRpcRequestParams params) {
    LOG.trace(
        "Calling {}(inclusionListConfiguration={})",
        getVersionedName(),
        INCLUSION_LIST_CONFIGURATION);

    final SchemaDefinitionsElectra schemaDefinitionsElectra =
        specVersion.getSchemaDefinitions().toVersionElectra().orElseThrow();

    final TransactionSchema transactionSchema =
        schemaDefinitionsElectra.getInclusionListSchema().getTransactionSchema();

    return executionEngineClient
        .getInclusionListV1(INCLUSION_LIST_CONFIGURATION)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(
            response ->
                new GetInclusionListResponse(
                    schemaDefinitionsElectra
                        .getInclusionListSchema()
                        .getSummarySchema()
                        .createFromElements(
                            response.inclusionListSummary().stream()
                                .map(address -> SszByteVector.fromBytes(address.getWrappedBytes()))
                                .toList()),
                    schemaDefinitionsElectra
                        .getInclusionListSchema()
                        .getTransactionsSchema()
                        .createFromElements(
                            response.inclusionListTransactions().stream()
                                .map(transactionSchema::fromBytes)
                                .toList())));
  }
}
