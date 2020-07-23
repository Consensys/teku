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

package tech.pegasys.teku.storage.server.fs;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class FsStorage implements AutoCloseable {
  private static final Logger LOG = LogManager.getLogger();

  private final Path baseDir;
  private final FsIndex index;

  public FsStorage(final Path baseDir, final FsIndex index) {
    this.baseDir = baseDir;
    this.index = index;
  }

  public Transaction startTransaction() {
    return new Transaction(index.startTransaction());
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return index.getCheckpoint(CheckpointType.FINALIZED);
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return index.getCheckpoint(CheckpointType.JUSTIFIED);
  }

  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return index.getCheckpoint(CheckpointType.BEST_JUSTIFIED);
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return load(blockPath(blockRoot), SignedBeaconBlock.class);
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockBySlot(final UnsignedLong slot) {
    return index.getFinalizedBlockRootBySlot(slot).flatMap(this::getBlockByBlockRoot);
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UnsignedLong slot) {
    return index.getLatestFinalizedBlockRootAtSlot(slot).flatMap(this::getBlockByBlockRoot);
  }

  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UnsignedLong startSlot, final UnsignedLong endSlot) {
    return index.getFinalizedBlockRoots(startSlot, endSlot).stream()
        .flatMap(blockRoot -> getBlockByBlockRoot(blockRoot).stream());
  }

  public Optional<BeaconState> getStateByBlockRoot(final Bytes32 blockRoot) {
    return index.getStateRootByBlockRoot(blockRoot).flatMap(this::getStateByStateRoot);
  }

  public Optional<BeaconState> getStateByStateRoot(final Bytes32 stateRoot) {
    return this.load(statePath(stateRoot), BeaconStateImpl.class);
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return index.getSlotAndBlockRootFromStateRoot(stateRoot);
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UnsignedLong maxSlot) {
    return index
        .getLatestAvailableStateRoot(maxSlot)
        .map(
            stateRoot ->
                getStateByStateRoot(stateRoot)
                    .orElseThrow(
                        () ->
                            new DatabaseStorageException(
                                "Expected state with root "
                                    + stateRoot
                                    + " to be available but was not found")));
  }

  public Map<Bytes32, Bytes32> getHotBlockChildToParentLookup() {
    return index.getHotBlockChildToParentLookup();
  }

  public Map<UnsignedLong, VoteTracker> loadVotes() {
    return index.loadVotes();
  }

  public void prune() {}

  private <T> Optional<T> load(final Path path, final Class<? extends T> type) {
    try {
      return Optional.of(
          SimpleOffsetSerializer.deserialize(Bytes.wrap(Files.readAllBytes(path)), type));
    } catch (final FileNotFoundException e) {
      return Optional.empty();
    } catch (IOException e) {
      throw new DatabaseStorageException(
          "Failed to load " + type.getSimpleName() + " from " + path, e);
    }
  }

  private Path blockPath(final SignedBeaconBlock block) {
    return blockPath(block.getRoot());
  }

  private Path blockPath(final Bytes32 blockRoot) {
    return baseDir.resolve("blocks").resolve(blockRoot.toUnprefixedHexString());
  }

  private Path statePath(final BeaconState state) {
    return statePath(state.hash_tree_root());
  }

  private Path statePath(final Bytes32 stateRoot) {
    return baseDir.resolve("states").resolve(stateRoot.toUnprefixedHexString());
  }

  @Override
  public void close() {
    index.close();
  }

  public class Transaction implements AutoCloseable {
    private final List<Path> toDelete = new ArrayList<>();
    private final FsIndex.Transaction indexTransaction;

    public Transaction(final FsIndex.Transaction indexTransaction) {
      this.indexTransaction = indexTransaction;
    }

    public void storeJustifiedCheckpoint(final Checkpoint checkpoint) {
      indexTransaction.setCheckpoint(CheckpointType.JUSTIFIED, checkpoint);
    }

    public void storeBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      indexTransaction.setCheckpoint(CheckpointType.BEST_JUSTIFIED, checkpoint);
    }

    public void storeFinalizedCheckpoint(final Checkpoint checkpoint) {
      indexTransaction.setCheckpoint(CheckpointType.FINALIZED, checkpoint);
    }

    public void storeBlock(final SignedBeaconBlock block, final boolean finalized) {
      indexTransaction.addBlock(block, finalized);
      write(blockPath(block), block);
    }

    public void finalizeBlock(final SignedBeaconBlock block) {
      final boolean existingBlock = indexTransaction.finalizeBlock(block);
      if (!existingBlock) {
        storeBlock(block, true);
      }
    }

    public void deleteBlock(final Bytes32 blockRoot) {
      indexTransaction.deleteBlock(blockRoot);
      delete(blockPath(blockRoot));
    }

    public void storeState(final BeaconState state) {
      indexTransaction.addState(
          state.hash_tree_root(),
          state.getSlot(),
          state.getLatest_block_header().hash_tree_root(),
          true);
      write(statePath(state), state);
    }

    public void storeStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
      indexTransaction.addState(
          stateRoot, slotAndBlockRoot.getSlot(), slotAndBlockRoot.getBlockRoot(), false);
    }

    public void deleteStateByBlockRoot(final Bytes32 blockRoot) {
      final Optional<Bytes32> deletedStateRoot = indexTransaction.deleteStateByBlockRoot(blockRoot);
      deletedStateRoot.ifPresent(stateRoot -> delete(statePath(stateRoot)));
    }

    public void storeVotes(final Map<UnsignedLong, VoteTracker> votes) {
      indexTransaction.storeVotes(votes);
    }

    @Override
    public void close() {
      indexTransaction.close();
    }

    private void write(final Path path, final SimpleOffsetSerializable data) {
      if (path.toFile().exists()) {
        return;
      }
      final File targetDirectory = path.getParent().toFile();
      if (!targetDirectory.mkdirs() && !targetDirectory.isDirectory()) {
        throw new DatabaseStorageException("Failed to create directory " + targetDirectory);
      }
      try {
        Files.write(
            path,
            SimpleOffsetSerializer.serialize(data).toArrayUnsafe(),
            StandardOpenOption.CREATE_NEW);
      } catch (final FileAlreadyExistsException e) {
        // File already written, ignore
      } catch (final IOException e) {
        throw new DatabaseStorageException("Failed to store data to path " + path, e);
      }
    }

    private void delete(final Path path) {
      // Files are only actually deleted after the commit is successful
      toDelete.add(path);
    }

    public void commit() {
      indexTransaction.commit();
      toDelete.forEach(
          path -> {
            final File file = path.toFile();
            if (!file.delete() && file.exists()) {
              LOG.warn("Unable to delete block data " + path);
            }
          });
      close();
    }
  }
}
