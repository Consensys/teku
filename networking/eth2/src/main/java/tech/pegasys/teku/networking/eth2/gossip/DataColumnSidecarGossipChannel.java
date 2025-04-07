/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.networking.eth2.gossip;

import java.util.List;
import tech.pegasys.teku.infrastructure.events.VoidReturningChannelInterface;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public interface DataColumnSidecarGossipChannel extends VoidReturningChannelInterface {

  DataColumnSidecarGossipChannel NOOP = (dataColumnSidecar, origin) -> {};

  default void publishDataColumnSidecars(
      final List<DataColumnSidecar> dataColumnSidecars, final RemoteOrigin origin) {
    dataColumnSidecars.forEach(
        dataColumnSidecar -> publishDataColumnSidecar(dataColumnSidecar, origin));
  }

  void publishDataColumnSidecar(DataColumnSidecar dataColumnSidecar, RemoteOrigin remoteOrigin);
}
