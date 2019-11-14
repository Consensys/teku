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

package tech.pegasys.artemis.storage;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.util.config.Constants;

public class ChainStorageClientSetup {
  private final AtomicInteger randomSeed = new AtomicInteger();
  private final EventBus eventBus = new EventBus();
  private final ChainStorageClient client = new ChainStorageClient(eventBus);

  public ChainStorageClient getClient() {
    return client;
  }

  public void initForGenesis() {
    // Setup genesis store
    final Store store = Store.get_genesis_store(DataStructureUtil.randomBeaconState(1));
    client.setStore(store);
  }

  public Optional<BeaconBlock> genesisBlock() {
    return blockStream()
        .filter(b -> b.getSlot().compareTo(UnsignedLong.valueOf(Constants.GENESIS_SLOT)) == 0)
        .findFirst();
  }

  /**
   * Creates blocks in a chain from {@code fromSlot} (inclusive) to {@code toSlot} (exclusive), and
   * add the blocks to the {@link Store}.
   *
   * @param fromSlot The slot at which the new chain begins.
   * @param toSlot The slot at which the new chain stops.
   * @return The list of created blocks which form a chain in ascending order.
   */
  public List<BeaconBlock> createChain(final long fromSlot, final long toSlot) {
    final List<BeaconBlock> newChain = new ArrayList<>();
    Optional<BeaconBlock> previousBlock = Optional.empty();
    for (long i = fromSlot; i < toSlot; i++) {
      UnsignedLong slot = UnsignedLong.valueOf(i);
      BeaconBlock newBlock = addBlockAtSlot(slot, previousBlock);
      newChain.add(newBlock);
      previousBlock = Optional.of(newBlock);
    }

    return newChain;
  }

  private BeaconBlock addBlockAtSlot(final UnsignedLong slot, final Optional<BeaconBlock> parent) {
    checkState(client.getStore() != null);
    final Transaction tx = client.getStore().startTransaction();

    Bytes32 parentRoot =
        parent
            .or(() -> blockStreamDesc().filter(b -> b.getSlot().compareTo(slot) < 0).findFirst())
            .map(b -> b.signing_root("signature"))
            .orElse(Bytes32.ZERO);

    BeaconBlock newBlock =
        DataStructureUtil.randomBeaconBlock(slot.longValue(), randomSeed.incrementAndGet());
    newBlock.setParent_root(parentRoot);
    tx.putBlock(newBlock.signing_root("signature"), newBlock);

    tx.commit();
    return newBlock;
  }

  /** Streams available blocks */
  private Stream<BeaconBlock> blockStream() {
    final Store store = client.getStore();
    if (store == null) {
      return Stream.empty();
    }
    return store.getBlockRoots().stream().map(store::getBlock);
  }

  /**
   * Return stream of blocks in descending order by slot
   *
   * @return stream of blocks ordered by slot number
   */
  private Stream<BeaconBlock> blockStreamDesc() {
    return blockStream().sorted(Comparator.comparing(BeaconBlock::getSlot).reversed());
  }
}
