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

import org.ethereum.beacon.chain.MutableBeaconChain.ImportResult;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.beacon.stream.SimpleProcessor;
import org.reactivestreams.Publisher;

public class ProposedBlockProcessorImpl implements ProposedBlockProcessor {

  private final SimpleProcessor<BeaconBlock> blocksStream;

  private final MutableBeaconChain beaconChain;

  public ProposedBlockProcessorImpl(MutableBeaconChain beaconChain, Schedulers schedulers) {
    this.beaconChain = beaconChain;
    blocksStream = new SimpleProcessor<>(schedulers.events(), "ProposedBlocksProcessor.blocks");
  }

  @Override
  public void newBlockProposed(BeaconBlock newBlock) {
    ImportResult result = beaconChain.insert(newBlock);
    if (result == ImportResult.OK) {
      blocksStream.onNext(newBlock);
    }
  }

  @Override
  public Publisher<BeaconBlock> processedBlocksStream() {
    return blocksStream;
  }
}
