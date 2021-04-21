/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.peers;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class StubSyncSource implements SyncSource {

  private final List<Request> requests = new ArrayList<>();
  private Optional<SafeFuture<Void>> currentRequest = Optional.empty();
  private Optional<RpcResponseListener<SignedBeaconBlock>> currentListener = Optional.empty();

  public void receiveBlocks(final SignedBeaconBlock... blocks) {
    final RpcResponseListener<SignedBeaconBlock> listener = currentListener.orElseThrow();
    Stream.of(blocks).forEach(response -> assertThat(listener.onResponse(response)).isCompleted());
    currentRequest.orElseThrow().complete(null);
  }

  public void failRequest(final Throwable error) {
    currentRequest.orElseThrow().completeExceptionally(error);
  }

  @Override
  public SafeFuture<Void> requestBlocksByRange(
      final UInt64 startSlot,
      final UInt64 count,
      final UInt64 step,
      final RpcResponseListener<SignedBeaconBlock> listener) {
    checkArgument(count.isGreaterThan(UInt64.ZERO), "Count must be greater than zero");
    checkArgument(step.isGreaterThan(UInt64.ZERO), "Step must be greater than zero");
    requests.add(new Request(startSlot, count));
    final SafeFuture<Void> request = new SafeFuture<>();
    currentRequest = Optional.of(request);
    currentListener = Optional.of(listener);
    return request;
  }

  @Override
  public SafeFuture<Void> disconnectCleanly(final DisconnectReason reason) {
    return SafeFuture.COMPLETE;
  }

  public void assertRequestedBlocks(final long startSlot, final long count) {
    assertThat(requests).contains(new Request(UInt64.valueOf(startSlot), UInt64.valueOf(count)));
  }

  @Override
  public void adjustReputation(final ReputationAdjustment adjustment) {}

  private static final class Request {
    private final UInt64 start;
    private final UInt64 count;

    private Request(final UInt64 start, final UInt64 count) {
      this.start = start;
      this.count = count;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Request request = (Request) o;
      return Objects.equals(start, request.start) && Objects.equals(count, request.count);
    }

    @Override
    public int hashCode() {
      return Objects.hash(start, count);
    }
  }
}
