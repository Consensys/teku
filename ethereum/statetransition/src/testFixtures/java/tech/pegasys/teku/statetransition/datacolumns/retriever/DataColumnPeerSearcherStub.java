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

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DataColumnPeerSearcherStub implements DataColumnPeerSearcher {

  public static class PeerSearchRequestStub implements PeerSearchRequest {
    private final UInt64 slot;
    private final UInt64 columnIndex;
    private boolean disposed;

    public PeerSearchRequestStub(final UInt64 slot, final UInt64 columnIndex) {
      this.slot = slot;
      this.columnIndex = columnIndex;
    }

    @Override
    public void dispose() {
      disposed = true;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public UInt64 getColumnIndex() {
      return columnIndex;
    }

    public boolean isDisposed() {
      return disposed;
    }
  }

  private final List<PeerSearchRequestStub> requests = new ArrayList<>();

  @Override
  public PeerSearchRequest requestPeers(final UInt64 slot, final UInt64 columnIndex) {
    PeerSearchRequestStub request = new PeerSearchRequestStub(slot, columnIndex);
    requests.add(request);
    return request;
  }

  public List<PeerSearchRequestStub> getRequests() {
    return requests;
  }
}
