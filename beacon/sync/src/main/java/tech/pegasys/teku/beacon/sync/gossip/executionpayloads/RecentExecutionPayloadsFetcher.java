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

package tech.pegasys.teku.beacon.sync.gossip.executionpayloads;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSyncService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.ServiceFacade;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.statetransition.block.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;

public interface RecentExecutionPayloadsFetcher
    extends ServiceFacade, ReceivedExecutionPayloadEventsChannel {

  RecentExecutionPayloadsFetcher NOOP =
      new RecentExecutionPayloadsFetcher() {
        @Override
        public void subscribeExecutionPayloadFetched(final ExecutionPayloadSubscriber subscriber) {}

        @Override
        public void requestRecentExecutionPayload(final Bytes32 blockRoot) {}

        @Override
        public void cancelRecentExecutionPayloadRequest(final Bytes32 blockRoot) {}

        @Override
        public SafeFuture<?> start() {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<?> stop() {
          return SafeFuture.COMPLETE;
        }

        @Override
        public boolean isRunning() {
          return false;
        }

        @Override
        public void onExecutionPayloadImported(
            final SignedExecutionPayloadEnvelope executionPayload,
            final boolean executionOptimistic) {}
      };

  static RecentExecutionPayloadsFetcher create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final ExecutionPayloadManager executionPayloadManager,
      final ForwardSyncService forwardSyncService,
      final FetchTaskFactory fetchTaskFactory) {
    final RecentExecutionPayloadsFetcher recentExecutionPayloadsFetcher;
    if (spec.isMilestoneSupported(SpecMilestone.EIP7732)) {
      recentExecutionPayloadsFetcher =
          RecentExecutionPayloadsFetchService.create(
              asyncRunner, forwardSyncService, executionPayloadManager, fetchTaskFactory);
    } else {
      recentExecutionPayloadsFetcher = RecentExecutionPayloadsFetcher.NOOP;
    }

    return recentExecutionPayloadsFetcher;
  }

  void subscribeExecutionPayloadFetched(ExecutionPayloadSubscriber subscriber);

  void requestRecentExecutionPayload(Bytes32 blockRoot);

  void cancelRecentExecutionPayloadRequest(Bytes32 blockRoot);
}
