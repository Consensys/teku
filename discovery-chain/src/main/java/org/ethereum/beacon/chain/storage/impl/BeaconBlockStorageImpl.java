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

package org.ethereum.beacon.chain.storage.impl;

import static java.util.Collections.singletonList;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.beacon.chain.storage.BeaconBlockStorage;
import org.ethereum.beacon.consensus.hasher.ObjectHasher;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.db.source.CodecSource;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.HoleyList;
import org.ethereum.beacon.db.source.impl.DataSourceList;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64s;

public class BeaconBlockStorageImpl implements BeaconBlockStorage {

  private final ObjectHasher<Hash32> objectHasher;

  @SSZSerializable
  public static class SlotBlocks {

    @SSZ private final List<Hash32> blockHashes;

    SlotBlocks(Hash32 blockHash) {
      this(singletonList(blockHash));
    }

    public SlotBlocks(List<Hash32> blockHashes) {
      this.blockHashes = blockHashes;
    }

    public List<Hash32> getBlockHashes() {
      return blockHashes;
    }

    SlotBlocks addBlock(Hash32 newBlock) {
      List<Hash32> blocks = new ArrayList<>(getBlockHashes());
      blocks.add(newBlock);
      return new SlotBlocks(blocks);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("blockHashes", blockHashes).toString();
    }
  }

  private final DataSource<Hash32, BeaconBlock> rawBlocks;
  private final HoleyList<SlotBlocks> blockIndex;
  private final boolean checkBlockExistOnAdd;
  private final boolean checkParentExistOnAdd;

  public BeaconBlockStorageImpl(
      ObjectHasher<Hash32> objectHasher,
      DataSource<Hash32, BeaconBlock> rawBlocks,
      HoleyList<SlotBlocks> blockIndex) {
    this(objectHasher, rawBlocks, blockIndex, true, true);
  }

  /**
   * @param objectHasher object hasher
   * @param rawBlocks hash -> block datasource
   * @param blockIndex slot -> blocks datasource
   * @param checkBlockExistOnAdd asserts that no duplicate blocks added (adds some overhead)
   * @param checkParentExistOnAdd asserts that added block parent is already here (adds some
   *     overhead)
   */
  public BeaconBlockStorageImpl(
      ObjectHasher<Hash32> objectHasher,
      DataSource<Hash32, BeaconBlock> rawBlocks,
      HoleyList<SlotBlocks> blockIndex,
      boolean checkBlockExistOnAdd,
      boolean checkParentExistOnAdd) {
    this.objectHasher = objectHasher;
    this.rawBlocks = rawBlocks;
    this.blockIndex = blockIndex;
    this.checkBlockExistOnAdd = checkBlockExistOnAdd;
    this.checkParentExistOnAdd = checkParentExistOnAdd;
  }

  @Override
  public SlotNumber getMaxSlot() {
    return SlotNumber.of(blockIndex.size() - 1);
  }

  @Override
  public List<Hash32> getSlotBlocks(SlotNumber slot) {
    return blockIndex
        .get(slot.getValue())
        .map(slotBlocks -> (List<Hash32>) new ArrayList<>(slotBlocks.getBlockHashes()))
        .orElse(Collections.emptyList());
  }

  @Override
  public Optional<BeaconBlock> get(@Nonnull Hash32 key) {
    return rawBlocks.get(key);
  }

  @Override
  public void put(@Nonnull Hash32 newBlockHash, @Nonnull BeaconBlock newBlock) {
    if (checkBlockExistOnAdd) {
      if (get(newBlockHash).isPresent()) {
        throw new IllegalArgumentException(
            "Block with hash already exists in storage: " + newBlock);
      }
    }

    if (!isEmpty() && checkParentExistOnAdd) {
      if (!get(newBlock.getParentRoot()).isPresent()) {
        throw new IllegalArgumentException("No parent found for added block: " + newBlock);
      }
    }

    rawBlocks.put(newBlockHash, newBlock);
    SlotBlocks slotBlocks = new SlotBlocks(newBlockHash);
    blockIndex.update(
        newBlock.getSlot().getValue(), blocks -> blocks.addBlock(newBlockHash), () -> slotBlocks);
  }

  @Override
  public void put(BeaconBlock block) {
    this.put(objectHasher.getHashTruncateLast(block), block);
  }

  @Override
  public void remove(@Nonnull Hash32 key) {
    Optional<BeaconBlock> block = rawBlocks.get(key);
    if (block.isPresent()) {
      rawBlocks.remove(key);
      SlotBlocks slotBlocks =
          blockIndex
              .get(block.get().getSlot().getValue())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Internal error: rawBlocks contains block, but blockIndex misses: "
                              + key));
      List<Hash32> newBlocks = new ArrayList<>(slotBlocks.getBlockHashes());
      newBlocks.remove(key);
      blockIndex.put(block.get().getSlot().getValue(), new SlotBlocks(newBlocks));
    }
  }

  @Override
  public List<BeaconBlock> getChildren(@Nonnull Hash32 parent, int limit) {
    Optional<BeaconBlock> block = get(parent);
    if (!block.isPresent()) {
      return Collections.emptyList();
    }
    BeaconBlock start = block.get();
    final List<BeaconBlock> children = new ArrayList<>();

    for (SlotNumber curSlot = start.getSlot().increment();
        curSlot.lessEqual(UInt64s.min(start.getSlot().plus(limit), getMaxSlot()));
        curSlot = curSlot.increment()) {
      getSlotBlocks(curSlot).stream()
          .map(this::get)
          .filter(Optional::isPresent)
          .filter(b -> b.get().getParentRoot().equals(parent))
          .forEach(b -> children.add(b.get()));
    }

    return children;
  }

  @Override
  public void flush() {
    // nothing to be done here. No cached data in this implementation
  }

  public static BeaconBlockStorageImpl create(
      Database database, ObjectHasher<Hash32> objectHasher, SerializerFactory serializerFactory) {
    DataSource<BytesValue, BytesValue> backingBlockSource = database.createStorage("beacon-block");
    DataSource<BytesValue, BytesValue> backingIndexSource =
        database.createStorage("beacon-block-index");

    DataSource<Hash32, BeaconBlock> blockSource =
        new CodecSource<>(
            backingBlockSource,
            key -> key,
            serializerFactory.getSerializer(BeaconBlock.class),
            serializerFactory.getDeserializer(BeaconBlock.class));
    HoleyList<SlotBlocks> indexSource =
        new DataSourceList<>(
            backingIndexSource,
            serializerFactory.getSerializer(SlotBlocks.class),
            serializerFactory.getDeserializer(SlotBlocks.class));

    return new BeaconBlockStorageImpl(objectHasher, blockSource, indexSource);
  }
}
