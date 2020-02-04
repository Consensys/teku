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

package tech.pegasys.artemis.pow;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;

class BatchByBlockDepositHandler {
  private static final Logger LOG = LogManager.getLogger();
  private final Consumer<DepositsFromBlockEvent> eventConsumer;
  private Optional<Block> currentBlock = Optional.empty();
  private List<Deposit> deposits = new ArrayList<>();

  BatchByBlockDepositHandler(final Consumer<DepositsFromBlockEvent> eventConsumer) {
    this.eventConsumer = eventConsumer;
  }

  public synchronized void onDepositEvent(final EthBlock.Block block, final Deposit event) {
    LOG.trace(
        "Received deposit from block {} with index {}",
        block.getNumber(),
        event.getMerkle_tree_index());
    checkForCompletedBlocks(block);
    deposits.add(event);
  }

  public synchronized void publishPendingBlock() {
    currentBlock.ifPresent(this::publishPendingBlock);
  }

  private void checkForCompletedBlocks(final EthBlock.Block blockOfNextDeposit) {
    if (currentBlock.isEmpty()) {
      currentBlock = Optional.of(blockOfNextDeposit);
      return;
    }
    final Block block = currentBlock.get();
    if (block.getHash().equals(blockOfNextDeposit.getHash())) {
      return;
    }
    publishPendingBlock(block);
    currentBlock = Optional.of(blockOfNextDeposit);
  }

  private void publishPendingBlock(final Block block) {
    LOG.trace("Publishing {} deposits for block {}", deposits::size, block::getNumber);
    eventConsumer.accept(
        new DepositsFromBlockEvent(
            UnsignedLong.valueOf(block.getNumber()),
            Bytes32.fromHexString(block.getHash()),
            UnsignedLong.valueOf(block.getTimestamp()),
            deposits));
    deposits = new ArrayList<>();
    currentBlock = Optional.empty();
  }
}
