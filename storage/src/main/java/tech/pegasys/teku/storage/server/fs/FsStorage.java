package tech.pegasys.teku.storage.server.fs;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class FsStorage {
  private static final Logger LOG = LogManager.getLogger();

  private final Path baseDir;
  private final FsIndex index;

  private FsStorage(final Path baseDir, final FsIndex index) {
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

  public Optional<BeaconState> getStateByBlockRoot(final Bytes32 blockRoot) {
    return index
        .getStateRootByBlockRoot(blockRoot)
        .flatMap(stateRoot -> load(statePath(stateRoot), BeaconState.class));
  }

  public Map<Bytes32, Bytes32> getHotBlockChildToParentLookup() {
    return index.getHotBlockChildToParentLookup();
  }

  public Map<UnsignedLong, VoteTracker> loadVotes() {
    return index.loadVotes();
  }

  private <T> Optional<T> load(final Path path, final Class<T> type) {
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

  public void prune() {}

  public class Transaction implements AutoCloseable {
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
      indexTransaction.addState(state);
      write(statePath(state), state);
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
      final File file = path.toFile();
      if (!file.delete() && file.exists()) {
        LOG.warn("Unable to delete block data " + path);
      }
    }

    public void commit() {
      indexTransaction.commit();
      close();
    }
  }
}
