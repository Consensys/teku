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

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import java.util.List;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRootReqResp;

public class LoggingBatchDataColumnsByRootReqResp implements BatchDataColumnsByRootReqResp {
  private final BatchDataColumnsByRootReqResp delegate;
  private final DasReqRespLogger logger;

  public LoggingBatchDataColumnsByRootReqResp(
      final BatchDataColumnsByRootReqResp delegate, final DasReqRespLogger logger) {
    this.delegate = delegate;
    this.logger = logger;
  }

  @Override
  public AsyncStream<DataColumnSidecar> requestDataColumnSidecarsByRoot(
      final UInt256 nodeId, final List<DataColumnsByRootIdentifier> columnIdentifiers) {
    final ReqRespResponseLogger<DataColumnSidecar> responseLogger =
        logger
            .getDataColumnSidecarsByRootLogger()
            .onOutboundRequest(LoggingPeerId.fromNodeId(nodeId), columnIdentifiers);
    return delegate
        .requestDataColumnSidecarsByRoot(nodeId, columnIdentifiers)
        .peek(responseLogger.asAsyncStreamVisitor());
  }

  @Override
  public int getCurrentRequestLimit(final UInt256 nodeId) {
    return delegate.getCurrentRequestLimit(nodeId);
  }
}
