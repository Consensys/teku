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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class DataColumnReqRespImpl implements DataColumnReqResp {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  // TODO: suppress until real ban is commented out
  @SuppressWarnings("unused")
  private final DataColumnPeerManager peerManager;

  private final DataColumnReqResp reqResp;

  public DataColumnReqRespImpl(DataColumnPeerManager peerManager, DataColumnReqResp reqResp) {
    this.peerManager = peerManager;
    this.reqResp = reqResp;
  }

  @Override
  public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
      UInt256 nodeId, DataColumnIdentifier columnIdentifier) {
    return reqResp
        .requestDataColumnSidecar(nodeId, columnIdentifier)
        .whenException(
            ex -> {
              if (ex instanceof DataColumnReqRespException) {
                LOG.debug(
                    "Error requesting data column sidecar {} from {}: {}",
                    columnIdentifier,
                    nodeId,
                    ex);
              } else {
                LOG.warn(
                    "Error requesting data column sidecar {} from {}: {}",
                    columnIdentifier,
                    nodeId,
                    ex);
                // TODO: uncomment in production
                // peerManager.banNode(nodeId);
              }
            });
  }

  @Override
  public int getCurrentRequestLimit(UInt256 nodeId) {
    return reqResp.getCurrentRequestLimit(nodeId);
  }

  @Override
  public void flush() {
    reqResp.flush();
  }
}
