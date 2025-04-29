/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.List;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;

public class DataColumnSidecarsByRootListenerValidatingProxy
    extends DataColumnSidecarsByRootValidator implements RpcResponseListener<DataColumnSidecar> {

  private final RpcResponseListener<DataColumnSidecar> listener;

  public DataColumnSidecarsByRootListenerValidatingProxy(
      final Peer peer,
      final Spec spec,
      final RpcResponseListener<DataColumnSidecar> listener,
      final KZG kzg,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final List<DataColumnsByRootIdentifier> expectedByRootIdentifiers) {
    super(
        peer,
        spec,
        kzg,
        metricsSystem,
        timeProvider,
        expectedByRootIdentifiers.stream()
            .flatMap(
                byRootIdentifier ->
                    byRootIdentifier.getColumns().stream()
                        .map(
                            column ->
                                new DataColumnIdentifier(byRootIdentifier.getBlockRoot(), column)))
            .toList());
    this.listener = listener;
  }

  @Override
  public SafeFuture<?> onResponse(final DataColumnSidecar dataColumnSidecar) {
    return SafeFuture.of(
        () -> {
          validate(dataColumnSidecar);
          return listener.onResponse(dataColumnSidecar);
        });
  }
}
