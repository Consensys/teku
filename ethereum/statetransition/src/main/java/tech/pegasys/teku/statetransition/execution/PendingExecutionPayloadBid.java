/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.execution;

import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;

public class PendingExecutionPayloadBid {
  private final SignedExecutionPayloadBid signedExecutionPayloadBid;
  private volatile boolean fromNetwork;

  public PendingExecutionPayloadBid(
      final SignedExecutionPayloadBid signedExecutionPayloadBid, final boolean fromNetwork) {
    this.signedExecutionPayloadBid = signedExecutionPayloadBid;
    this.fromNetwork = fromNetwork;
  }

  public SignedExecutionPayloadBid signedExecutionPayloadBid() {
    return signedExecutionPayloadBid;
  }

  public boolean fromNetwork() {
    return fromNetwork;
  }

  public void markAsLocal() {
    fromNetwork = false;
  }
}
