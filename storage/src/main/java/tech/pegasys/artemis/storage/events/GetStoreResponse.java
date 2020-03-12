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

package tech.pegasys.artemis.storage.events;

import java.util.Optional;
import tech.pegasys.artemis.storage.Store;

public class GetStoreResponse {
  private final long requestId;
  private final Optional<Store> store;

  public GetStoreResponse(final long requestId, final Optional<Store> store) {
    this.requestId = requestId;
    this.store = store;
  }

  public Optional<Store> getStore() {
    return store;
  }

  public long getRequestId() {
    return requestId;
  }
}
