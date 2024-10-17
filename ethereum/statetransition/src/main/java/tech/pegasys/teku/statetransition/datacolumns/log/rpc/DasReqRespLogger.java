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
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public interface DasReqRespLogger {

  record ByRangeRequest(UInt64 startSlot, int slotCount, List<UInt64> columnIndexes) {}

  static DasReqRespLogger create(TimeProvider timeProvider) {
    return new DasReqRespLoggerImpl(timeProvider);
  }

  DasReqRespLogger NOOP =
      new DasReqRespLogger() {
        @Override
        public ReqRespMethodLogger<List<DataColumnIdentifier>, DataColumnSidecar>
            getDataColumnSidecarsByRootLogger() {
          return new NoopReqRespMethodLogger<>();
        }

        @Override
        public ReqRespMethodLogger<ByRangeRequest, DataColumnSidecar>
            getDataColumnSidecarsByRangeLogger() {
          return new NoopReqRespMethodLogger<>();
        }
      };

  ReqRespMethodLogger<List<DataColumnIdentifier>, DataColumnSidecar>
      getDataColumnSidecarsByRootLogger();

  ReqRespMethodLogger<ByRangeRequest, DataColumnSidecar> getDataColumnSidecarsByRangeLogger();
}
