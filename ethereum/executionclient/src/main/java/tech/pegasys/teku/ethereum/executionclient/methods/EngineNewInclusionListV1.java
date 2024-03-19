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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.InclusionListConfigurationV1;
import tech.pegasys.teku.ethereum.executionclient.schema.InclusionListStatusV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionlayer.InclusionListStatus;

public class EngineNewInclusionListV1 extends AbstractEngineJsonRpcMethod<InclusionListStatus> {
  private static final Logger LOG = LogManager.getLogger();

  // hardcoded value fo now
  public static final InclusionListConfigurationV1 INCLUSION_LIST_CONFIGURATION =
      new InclusionListConfigurationV1(UInt64.valueOf(30_000_000));

  public EngineNewInclusionListV1(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_NEW_INCLUSION_LIST.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<InclusionListStatus> execute(final JsonRpcRequestParams params) {
    final List<Bytes20> inclusionListSummary = params.getRequiredListParameter(0, Bytes20.class);

    final List<Bytes> inclusionListTransactions = params.getRequiredListParameter(1, Bytes.class);
    final Bytes32 parentBeaconBlockRoot = params.getRequiredParameter(2, Bytes32.class);

    LOG.trace(
        "Calling {}(inclusionListSummary={}, inclusionListTransactions={}, parentBeaconBlockRoot={}, inclusionListConfiguration={})",
        getVersionedName(),
        inclusionListSummary,
        inclusionListTransactions,
        parentBeaconBlockRoot,
        INCLUSION_LIST_CONFIGURATION);

    return executionEngineClient
        .newInclusionListV1(
            inclusionListSummary,
            inclusionListTransactions,
            parentBeaconBlockRoot,
            INCLUSION_LIST_CONFIGURATION)
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(InclusionListStatusV1::asInternalExecutionPayload)
        .thenPeek(
            inclusionListStatus ->
                LOG.trace(
                    "Response {}(inclusionListSummary={}, inclusionListTransactions={}, parentBeaconBlockRoot={}, inclusionListConfiguration={}) -> {}",
                    getVersionedName(),
                    inclusionListSummary,
                    inclusionListTransactions,
                    parentBeaconBlockRoot,
                    INCLUSION_LIST_CONFIGURATION,
                    inclusionListStatus))
        .exceptionally(InclusionListStatus::failedExecution);
  }
}
