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

package tech.pegasys.teku.beacon.sync.gossip.blocks;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.service.serviceutils.ServiceFacade;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;

public interface RecentBlocksFetcher extends ServiceFacade, ReceivedBlockEventsChannel {

  void subscribeBlockFetched(BlockSubscriber subscriber);

  void requestRecentBlock(Bytes32 blockRoot);

  void cancelRecentBlockRequest(Bytes32 blockRoot);
}
