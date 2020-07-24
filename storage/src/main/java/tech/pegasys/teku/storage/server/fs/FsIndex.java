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
import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.Checkpoint;

/**
 * Table schema:
 *
 * <p>block (blockRoot, slot, parentRoot, finalized) indexes (blockRoot), (slot, finalized)
 *
 * <p>state (stateRoot, blockRoot, slot, stored) indexes (stateRoot), (blockRoot), (slot, stored)
 *
 * <p>checkpoint (type, blockRoot, epoch)
 *
 * <p>vote (validatorIndex, currentRoot, nextRoot, nextEpoch)
 */
public class FsIndex implements AutoCloseable {
  private final PlatformTransactionManager transactionManager;
  private final HikariDataSource dataSource;
  private final JdbcOperations jdbc;

  public FsIndex(PlatformTransactionManager transactionManager, final HikariDataSource dataSource) {
    this.transactionManager = transactionManager;
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  public Transaction startTransaction() {
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

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return loadSingle(
        "SELECT blockRoot, slot FROM state WHERE stateRoot = ?",
        (rs, rowNum) ->
            new SlotAndBlockRoot(getUnsignedLong(rs, "slot"), getBytes32(rs, "blockRoot")),
        (Object) stateRoot.toArrayUnsafe());
  }

  public Optional<Bytes32> getLatestAvailableStateRoot(final UnsignedLong maxSlot) {
    return loadSingle(
        "SELECT stateRoot FROM state WHERE slot <= ? AND stored = true ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getBytes32(rs, "stateRoot"),
        maxSlot.bigIntegerValue());
  }

  public Optional<Bytes32> getFinalizedBlockRootBySlot(final UnsignedLong slot) {
    return loadSingle(
        "SELECT blockRoot FROM block WHERE slot = ? AND finalized = true",
        (rs, rowNum) -> getBytes32(rs, "blockRoot"),
        slot.bigIntegerValue());
  }

  public Optional<Bytes32> getLatestFinalizedBlockRootAtSlot(final UnsignedLong slot) {
    return loadSingle(
        "SELECT blockRoot FROM block WHERE slot <= ? AND finalized = true ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getBytes32(rs, "blockRoot"),
        slot.bigIntegerValue());
  }

  public List<Bytes32> getFinalizedBlockRoots(
      final UnsignedLong startSlot, final UnsignedLong endSlot) {
    return jdbc.query(
        "SELECT blockRoot FROM block WHERE slot >= ? AND slot <= endSlot ORDER BY slot",
        (rs, rowNum) -> getBytes32(rs, "blockRoot"),
        startSlot.bigIntegerValue(),
        endSlot.bigIntegerValue());
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

  @Override
  public void close() {
    dataSource.close();
  }

  private <T> Optional<T> loadSingle(
      final String sql, final RowMapper<T> mapper, final Object... params) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(sql, mapper, params));
    } catch (final EmptyResultDataAccessException e) {
      return Optional.empty();
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
              + " ON DUPLICATE KEY UPDATE blockRoot = VALUES(blockRoot), epoch = VALUES(epoch)",
          type.name(),
          checkpoint.getRoot().toArrayUnsafe(),
          checkpoint.getEpoch().bigIntegerValue());
    }

    public void addBlock(final SignedBeaconBlock block, final boolean finalized) {
      execSql(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized) VALUES (?, ?, ?, ?) "
              + " ON DUPLICATE KEY UPDATE finalized = VALUES(finalized)",
          block.getRoot().toArrayUnsafe(),
          block.getSlot().bigIntegerValue(),
          block.getParent_root().toArrayUnsafe(),
          finalized);
    }

    public boolean finalizeBlock(final SignedBeaconBlock block) {
      return execSql(
              "UPDATE block SET finalized = true WHERE blockRoot = ?",
              (Object) block.getRoot().toArrayUnsafe())
          > 0;
    }

    public void deleteBlock(final Bytes32 blockRoot) {
      execSql("DELETE FROM block WHERE blockRoot = ?", (Object) blockRoot.toArrayUnsafe());
    }

    public void addState(
        final Bytes32 stateRoot,
        final UnsignedLong slot,
        final Bytes32 blockRoot,
        final boolean stored) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot, stored) VALUES (?, ?, ?, ?)"
              + " ON DUPLICATE KEY UPDATE stored = VALUES(stored)",
          stateRoot.toArrayUnsafe(),
          blockRoot.toArrayUnsafe(),
          slot.bigIntegerValue(),
          stored);
    }

    public Optional<Bytes32> deleteStateByBlockRoot(final Bytes32 blockRoot) {
      final Optional<Bytes32> maybeStateRoot = getStateRootByBlockRoot(blockRoot);
      maybeStateRoot.ifPresent(
          stateRoot ->
              execSql("DELETE FROM state WHERE stateRoot = ?", (Object) stateRoot.toArrayUnsafe()));
      return maybeStateRoot;
    }

    public void storeVotes(final Map<UnsignedLong, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, voteTracker) ->
              execSql(
                  "INSERT INTO vote (validatorIndex, currentRoot, nextRoot, nextEpoch) VALUES (?, ?, ?, ?)"
                      + " ON DUPLICATE KEY UPDATE currentRoot = VALUES(currentRoot),"
                      + "                     nextRoot = VALUES(nextRoot),"
                      + "                     nextEpoch = VALUES(nextEpoch)",
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
