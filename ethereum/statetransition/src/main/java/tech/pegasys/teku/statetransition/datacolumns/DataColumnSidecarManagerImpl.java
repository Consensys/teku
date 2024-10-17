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

package tech.pegasys.teku.statetransition.datacolumns;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DataColumnSidecarManagerImpl implements DataColumnSidecarManager {

  private final DataColumnSidecarGossipValidator validator;
  private final Subscribers<ValidDataColumnSidecarsListener> validDataColumnSidecarsSubscribers =
      Subscribers.create(true);
  private final DasGossipLogger dasGossipLogger;

  public DataColumnSidecarManagerImpl(
      DataColumnSidecarGossipValidator validator, DasGossipLogger dasGossipLogger) {
    this.validator = validator;
    this.dasGossipLogger = dasGossipLogger;
  }

  @Override
  public SafeFuture<InternalValidationResult> onDataColumnSidecarGossip(
      DataColumnSidecar dataColumnSidecar, Optional<UInt64> arrivalTimestamp) {
    return validator
        .validate(dataColumnSidecar)
        .thenPeek(
            res -> {
              dasGossipLogger.onReceive(dataColumnSidecar, res);
              if (res.isAccept()) {
                validDataColumnSidecarsSubscribers.forEach(
                    listener -> listener.onNewValidSidecar(dataColumnSidecar));
              }
            });
  }

  @Override
  public void onDataColumnSidecarPublish(DataColumnSidecar sidecar) {
    validDataColumnSidecarsSubscribers.forEach(l -> l.onNewValidSidecar(sidecar));
  }

  @Override
  public void subscribeToValidDataColumnSidecars(ValidDataColumnSidecarsListener sidecarsListener) {
    validDataColumnSidecarsSubscribers.subscribe(sidecarsListener);
  }
}
