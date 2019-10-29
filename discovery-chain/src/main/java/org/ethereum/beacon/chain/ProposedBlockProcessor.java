/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.chain;

import org.ethereum.beacon.core.BeaconBlock;
import org.reactivestreams.Publisher;

/**
 * Handles own proposed blocks, i.e. imports them to the chain and on success forwards them to the
 * outbound stream for further broadcasting
 */
public interface ProposedBlockProcessor {

  void newBlockProposed(BeaconBlock newBlcok);

  Publisher<BeaconBlock> processedBlocksStream();
}
