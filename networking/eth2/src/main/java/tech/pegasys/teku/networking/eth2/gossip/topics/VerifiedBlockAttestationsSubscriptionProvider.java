/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.statetransition.block.VerifiedBlockOperationsListener;

public interface VerifiedBlockAttestationsSubscriptionProvider {
  void subscribe(VerifiedBlockOperationsListener<Attestation> verifiedBlockOperationsListener);
}
