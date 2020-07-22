package tech.pegasys.teku.storage.server.fs;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

/**
 * Table schema:
 *
 * <p>block (blockRoot, slot, parentRoot, finalized) indexes (blockRoot), (slot, finalized)
 *
 * <p>state (stateRoot, blockRoot, slot) indexes (stateRoot), (blockRoot), (slot)
 *
 * <p>checkpoint (type, blockRoot, epoch)
 *
 * <p>vote (validatorIndex, currentRoot, nextRoot, nextEpoch)
 */
public class FsIndex {

  public Transaction startTransaction() {
    return new Transaction();
  }

  public Optional<Checkpoint> getCheckpoint(final CheckpointType type) {
    return loadSingle(
        "SELECT * FROM checkpoint WHERE type = ?",
        row -> new Checkpoint(row.<UnsignedLong>get("epoch"), row.<Bytes32>get("blockRoot")),
        type);
  }

  public Optional<Bytes32> getStateRootByBlockRoot(final Bytes32 blockRoot) {
    return loadSingle(
        "SELECT stateRoot FROM state WHERE blockRoot = ? ORDER BY slot LIMIT 1",
        row -> row.get("stateRoot"),
        blockRoot);
  }

  public Map<Bytes32, Bytes32> getHotBlockChildToParentLookup() {
    final Map<Bytes32, Bytes32> childToParentLookup = new HashMap<>();
    loadForEach(
        "SELECT blockRoot, parentRoot FROM block WHERE finalized = false",
        row -> childToParentLookup.put(row.get("blockRoot"), row.get("parentRoot")));
    return childToParentLookup;
  }

  public Map<UnsignedLong, VoteTracker> loadVotes() {
    final Map<UnsignedLong, VoteTracker> votes = new HashMap<>();
    loadForEach(
        "SELECT validatorIndex, currentRoot, nextRoot, nextEpoch from vote",
        row ->
            votes.put(
                row.get("validatorIndex"),
                new VoteTracker(
                    row.get("currentRoot"), row.get("nextRoot"), row.get("nextEpoch"))));
    return votes;
  }

  private <T> Optional<T> loadSingle(
      final String sql, final Function<SqlRow, T> mapper, final Object... params) {
    return Optional.empty();
  }

  private void loadForEach(
      final String sql, final Consumer<SqlRow> rowHandler, final Object... params) {}

  private interface SqlRow {
    <T> T get(final String field);
  }

  public static class Transaction implements AutoCloseable {

    public void setCheckpoint(final CheckpointType type, final Checkpoint checkpoint) {
      execSql(
          "INSERT INTO checkpoint (type, blockRoot, epoch) VALUES (?, ?, ?)"
              + " ON DUPLICATE UPDATE SET blockRoot = VALUES(blockRoot), epoch VALUES(epoch)",
          type,
          checkpoint.getRoot(),
          checkpoint.getEpoch());
    }

    public void addBlock(final SignedBeaconBlock block, final boolean finalized) {
      execSql(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized) VALUES (?, ?, ?, ?) "
              + " ON DUPLICATE UPDATE SET finalized = VALUES(finalized)",
          block.getRoot(),
          block.getSlot(),
          block.getParent_root(),
          finalized);
    }

    public boolean finalizeBlock(final SignedBeaconBlock block) {
      return execSql("UPDATE block SET finalzied = true WHERE blockRoot = ?", block.getRoot()) > 0;
    }

    public void deleteBlock(final Bytes32 blockRoot) {
      execSql("DELETE FROM block WHERE blockRoot = ?", blockRoot);
    }

    public void addState(final BeaconState state) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot) VALUES (?, ?, ?)",
          state.hash_tree_root(),
          state.getLatest_block_header().hash_tree_root(),
          state.getSlot());
    }

    public void storeVotes(final Map<UnsignedLong, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, voteTracker) -> {
            execSql(
                "INSERT INTO vote (validatorIndex, currentRoot, nextRoot, nextEpoch) VALUES (?, ?, ?, ?)"
                    + " ON DUPLICATE UPDATE SET currentRoot = VALUES(currentRoot),"
                    + "                     SET nextRoot = VALUES(nextRoot),"
                    + "                     SET nextEpoch = VALUES(nextEpoch)");
          });
    }

    // Returns number of affected rows.
    public void commit() {
      close();
    }

    @Override
    public void close() {}

    private int execSql(final String sql, final Object... params) {
      return 0;
    }
  }
}
