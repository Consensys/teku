package tech.pegasys.teku.storage.server.fs;

import com.google.common.primitives.UnsignedLong;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
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
private static final Logger LOG = LogManager.getLogger();
  private final PlatformTransactionManager transactionManager;
  private final JdbcOperations jdbc;

  public FsIndex(PlatformTransactionManager transactionManager, final JdbcOperations jdbc) {
    this.transactionManager = transactionManager;
    this.jdbc = jdbc;
  }

  public Transaction startTransaction() {
    LOG.info("Starting transaction");
    return new Transaction(transactionManager.getTransaction(TransactionDefinition.withDefaults()));
  }

  public Optional<Checkpoint> getCheckpoint(final CheckpointType type) {
    return loadSingle(
        "SELECT * FROM checkpoint WHERE type = ?",
        (rs, rowNum) -> new Checkpoint(getUnsignedLong(rs, "epoch"), getBytes32(rs, "blockRoot")),
        type.name());
  }

  public Optional<Bytes32> getStateRootByBlockRoot(final Bytes32 blockRoot) {
    return loadSingle(
        "SELECT stateRoot FROM state WHERE blockRoot = ? ORDER BY slot LIMIT 1",
        (rs, rowNum) -> getBytes32(rs, "stateRoot"),
        (Object) blockRoot.toArrayUnsafe());
  }

  public Map<Bytes32, Bytes32> getHotBlockChildToParentLookup() {
    final Map<Bytes32, Bytes32> childToParentLookup = new HashMap<>();
    loadForEach(
        "SELECT blockRoot, parentRoot FROM block WHERE finalized = false",
        rs -> childToParentLookup.put(getBytes32(rs, "blockRoot"), getBytes32(rs, "parentRoot")));
    return childToParentLookup;
  }

  public Map<UnsignedLong, VoteTracker> loadVotes() {
    final Map<UnsignedLong, VoteTracker> votes = new HashMap<>();
    loadForEach(
        "SELECT validatorIndex, currentRoot, nextRoot, nextEpoch from vote",
        rs ->
            votes.put(
                getUnsignedLong(rs, "validatorIndex"),
                new VoteTracker(
                    getBytes32(rs, "currentRoot"),
                    getBytes32(rs, "nextRoot"),
                    getUnsignedLong(rs, "nextEpoch"))));
    return votes;
  }

  private <T> Optional<T> loadSingle(
      final String sql, final RowMapper<T> mapper, final Object... params) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(sql, mapper, params));
    } catch (final IncorrectResultSizeDataAccessException e) {
      if (e.getActualSize() == 0) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private void loadForEach(
      final String sql, final RowCallbackHandler rowHandler, final Object... params) {
    jdbc.query(sql, rowHandler, params);
  }

  private UnsignedLong getUnsignedLong(final ResultSet rs, final String field) throws SQLException {
    return UnsignedLong.valueOf(rs.getBigDecimal(field).toBigIntegerExact());
  }

  private Bytes32 getBytes32(final ResultSet rs, final String field) throws SQLException {
    return Bytes32.wrap(rs.getBytes(field));
  }

  public class Transaction implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final TransactionStatus transaction;

    public Transaction(final TransactionStatus transaction) {
      this.transaction = transaction;
    }

    public void setCheckpoint(final CheckpointType type, final Checkpoint checkpoint) {
      execSql(
          "INSERT INTO checkpoint (type, blockRoot, epoch) VALUES (?, ?, ?)"
              + " ON DUPLICATE UPDATE SET blockRoot = VALUES(blockRoot), epoch VALUES(epoch)",
          type,
          checkpoint.getRoot().toArrayUnsafe(),
          checkpoint.getEpoch().bigIntegerValue());
    }

    public void addBlock(final SignedBeaconBlock block, final boolean finalized) {
      execSql(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized) VALUES (?, ?, ?, ?) "
              + " ON DUPLICATE UPDATE SET finalized = VALUES(finalized)",
          block.getRoot().toArrayUnsafe(),
          block.getSlot().bigIntegerValue(),
          block.getParent_root().toArrayUnsafe(),
          finalized);
    }

    public boolean finalizeBlock(final SignedBeaconBlock block) {
      return execSql(
              "UPDATE block SET finalzied = true WHERE blockRoot = ?",
              (Object) block.getRoot().toArrayUnsafe())
          > 0;
    }

    public void deleteBlock(final Bytes32 blockRoot) {
      execSql("DELETE FROM block WHERE blockRoot = ?", (Object) blockRoot.toArrayUnsafe());
    }

    public void addState(final BeaconState state) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot) VALUES (?, ?, ?)",
          state.hash_tree_root().toArrayUnsafe(),
          state.getLatest_block_header().hash_tree_root().toArrayUnsafe(),
          state.getSlot().bigIntegerValue());
    }

    public void storeVotes(final Map<UnsignedLong, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, voteTracker) ->
              execSql(
                  "INSERT INTO vote (validatorIndex, currentRoot, nextRoot, nextEpoch) VALUES (?, ?, ?, ?)"
                      + " ON DUPLICATE UPDATE SET currentRoot = VALUES(currentRoot),"
                      + "                     SET nextRoot = VALUES(nextRoot),"
                      + "                     SET nextEpoch = VALUES(nextEpoch)",
                  validatorIndex.bigIntegerValue(),
                  voteTracker.getCurrentRoot().toArrayUnsafe(),
                  voteTracker.getNextRoot().toArrayUnsafe(),
                  voteTracker.getNextEpoch().bigIntegerValue()));
    }

    // Returns number of affected rows.
    private int execSql(final String sql, final Object... params) {
      return jdbc.update(sql, params);
    }

    public void commit() {
      if (closed.compareAndSet(false, true)) {
        transactionManager.commit(transaction);
      }
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        transactionManager.rollback(transaction);
      }
    }
  }
}
