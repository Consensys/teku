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

import com.google.common.io.ByteStreams;
import com.google.common.primitives.UnsignedLong;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.xerial.snappy.Snappy;
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

  private final PlatformTransactionManager transactionManager;
  private final HikariDataSource dataSource;
  private final JdbcOperations jdbc;
  private final boolean compress;

  public FsStorage(
      PlatformTransactionManager transactionManager,
      final HikariDataSource dataSource,
      final boolean compress) {
    this.transactionManager = transactionManager;
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
    this.compress = compress;
  }

  public Transaction startTransaction() {
    return new Transaction(transactionManager.getTransaction(TransactionDefinition.withDefaults()));
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return getCheckpoint(CheckpointType.JUSTIFIED);
  }

  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return getCheckpoint(CheckpointType.BEST_JUSTIFIED);
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return getCheckpoint(CheckpointType.FINALIZED);
  }

  private Optional<Checkpoint> getCheckpoint(final CheckpointType type) {
    return loadSingle(
        "SELECT * FROM checkpoint WHERE type = ?",
        (rs, rowNum) -> new Checkpoint(getUnsignedLong(rs, "epoch"), getBytes32(rs, "blockRoot")),
        type.name());
  }

  public Optional<BeaconState> getStateByStateRoot(final Bytes32 stateRoot) {
    return loadSingle(
        "SELECT ssz FROM state WHERE stateRoot = ?",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class),
        (Object) stateRoot.toArrayUnsafe());
  }

  public Optional<BeaconState> getStateByBlockRoot(final Bytes32 blockRoot) {
    return loadSingle(
        "SELECT ssz FROM state WHERE blockRoot = ? ORDER BY slot LIMIT 1",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class),
        (Object) blockRoot.toArrayUnsafe());
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return loadSingle(
        "SELECT blockRoot, slot FROM state WHERE stateRoot = ?",
        (rs, rowNum) ->
            new SlotAndBlockRoot(getUnsignedLong(rs, "slot"), getBytes32(rs, "blockRoot")),
        (Object) stateRoot.toArrayUnsafe());
  }

  public Optional<BeaconState> getLatestFinalizedState() {
    return loadSingle("SELECT ssz FROM finalized_state",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class));
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UnsignedLong maxSlot) {
    return loadSingle(
        "SELECT ssz FROM state WHERE slot <= ? AND ssz IS NOT NULL ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class),
        maxSlot.bigIntegerValue());
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return loadSingle(
        "SELECT ssz FROM block WHERE blockRoot = ?",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        (Object) blockRoot.toArrayUnsafe());
  }

  private <T> T getSsz(final ResultSet rs, final Class<? extends T> type)
      throws SQLException {
    final Blob blob = rs.getBlob("ssz");
    if (blob == null) {
      return null;
    }
    try {
      final byte[] rawData = ByteStreams.toByteArray(blob.getBinaryStream());
      final Bytes data = Bytes.wrap(compress ? Snappy.uncompress(rawData) : rawData);
      return SimpleOffsetSerializer.deserialize(data, type);
    } catch (IOException e) {
      throw new SQLException("Failed to read BLOB content", e);
    } finally {
      blob.free();
    }
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockBySlot(final UnsignedLong slot) {
    return loadSingle(
        "SELECT blockRoot FROM block WHERE slot = ? AND finalized = true",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UnsignedLong slot) {
    return loadSingle(
        "SELECT ssz FROM block WHERE slot <= ? AND finalized = true ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UnsignedLong startSlot, final UnsignedLong endSlot) {
    final List<Bytes32> blockRoots =
        jdbc.query(
            "SELECT blockRoot FROM block WHERE finalized = true AND slot >= ? AND slot <= endSlot ORDER BY slot",
            (rs, rowNum) -> getBytes32(rs, "blockRoot"),
            startSlot.bigIntegerValue(),
            endSlot.bigIntegerValue());
    return blockRoots.stream().map(blockRoot -> getBlockByBlockRoot(blockRoot).orElseThrow());
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

    public void storeJustifiedCheckpoint(final Checkpoint checkpoint) {
      setCheckpoint(CheckpointType.JUSTIFIED, checkpoint);
    }

    public void storeBestJustifiedCheckpoint(final Checkpoint checkpoint) {
      setCheckpoint(CheckpointType.BEST_JUSTIFIED, checkpoint);
    }

    public void storeFinalizedCheckpoint(final Checkpoint checkpoint) {
      setCheckpoint(CheckpointType.FINALIZED, checkpoint);
    }

    private void setCheckpoint(final CheckpointType type, final Checkpoint checkpoint) {
      execSql(
          "INSERT INTO checkpoint (type, blockRoot, epoch) VALUES (?, ?, ?)"
              + " ON DUPLICATE KEY UPDATE blockRoot = VALUES(blockRoot), epoch = VALUES(epoch)",
          type.name(),
          checkpoint.getRoot().toArrayUnsafe(),
          checkpoint.getEpoch().bigIntegerValue());
    }

    public void storeBlock(final SignedBeaconBlock block, final boolean finalized) {
      execSql(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized, ssz) VALUES (?, ?, ?, ?, ?) "
              + " ON DUPLICATE KEY UPDATE finalized = VALUES(finalized)",
          block.getRoot().toArrayUnsafe(),
          block.getSlot().bigIntegerValue(),
          block.getParent_root().toArrayUnsafe(),
          finalized,
          serializeSsz(block));
    }

    public void finalizeBlock(final SignedBeaconBlock block) {
      execSql(
          "UPDATE block SET finalized = true WHERE blockRoot = ?",
          (Object) block.getRoot().toArrayUnsafe());
    }

    public void deleteBlock(final Bytes32 blockRoot) {
      execSql("DELETE FROM block WHERE blockRoot = ?", (Object) blockRoot.toArrayUnsafe());
    }

    public void storeStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot) VALUES (?, ?, ?)",
          stateRoot.toArrayUnsafe(),
          slotAndBlockRoot.getBlockRoot().toArrayUnsafe(),
          slotAndBlockRoot.getSlot().bigIntegerValue());
    }

    public void storeState(final Bytes32 blockRoot, final BeaconState state) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot, ssz) VALUES (?, ?, ?, ?)"
              + " ON DUPLICATE KEY UPDATE ssz = VALUES(ssz)",
          state.hash_tree_root().toArrayUnsafe(),
          blockRoot.toArrayUnsafe(),
          state.getSlot().bigIntegerValue(),
          serializeSsz(state));
    }

    public void storeLatestFinalizedState(final BeaconState state) {
      execSql(
          "INSERT INTO finalized_state (id, ssz) VALUES (1, ?) ON DUPLICATE KEY UPDATE ssz = VALUES(ssz)",
          serializeSsz(state));
    }

    public void deleteStateByBlockRoot(final Bytes32 blockRoot) {
      execSql("DELETE FROM state WHERE blockRoot = ?", (Object) blockRoot.toArrayUnsafe());
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

    private Object serializeSsz(final SimpleOffsetSerializable obj) {
      try {
        final byte[] uncompressed = SimpleOffsetSerializer.serialize(obj).toArrayUnsafe();
        return compress ? Snappy.compress(uncompressed) : uncompressed;
      } catch (final IOException e) {
        throw new DatabaseStorageException("Compression failed", e);
      }
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
