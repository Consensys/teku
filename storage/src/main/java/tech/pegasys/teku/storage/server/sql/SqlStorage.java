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

package tech.pegasys.teku.storage.server.sql;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
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
import org.apache.tuweni.bytes.Bytes48;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.xerial.snappy.Snappy;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.storage.server.DatabaseStorageException;

public class SqlStorage implements AutoCloseable {

  private final PlatformTransactionManager transactionManager;
  private final HikariDataSource dataSource;
  private final JdbcOperations jdbc;
  private final boolean compress;

  public SqlStorage(
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

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return getCheckpoint(CheckpointType.WEAK_SUBJECTIVITY);
  }

  private Optional<Checkpoint> getCheckpoint(final CheckpointType type) {
    return loadSingle(
        "SELECT * FROM checkpoint WHERE type = ?",
        (rs, rowNum) -> new Checkpoint(getUInt64(rs, "epoch"), getBytes32(rs, "blockRoot")),
        type.name());
  }

  public Optional<BeaconState> getStateByStateRoot(final Bytes32 stateRoot) {
    return loadSingle(
        "SELECT ssz FROM state WHERE stateRoot = ?",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class),
        (Object) stateRoot.toArrayUnsafe());
  }

  public Optional<BeaconState> getStateByBlockRoot(final Bytes32 blockRoot) {
    return getStateByBlockRoot(blockRoot, false);
  }

  public Optional<BeaconState> getStateByBlockRoot(
      final Bytes32 blockRoot, final boolean onlyNonfinalized) {
    final String sql;
    if (onlyNonfinalized) {
      sql =
          "   SELECT s.ssz FROM state s "
              + "     JOIN block b ON b.blockRoot = s.blockRoot "
              + "    WHERE s.blockRoot = ? "
              + "      AND b.finalized = 0 "
              + " ORDER BY s.slot LIMIT 1";
    } else {
      sql = "SELECT ssz FROM state WHERE blockRoot = ? ORDER BY slot LIMIT 1";
    }
    return loadSingle(
        sql, (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class), (Object) blockRoot.toArrayUnsafe());
  }

  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return loadSingle(
        "SELECT blockRoot, slot FROM state WHERE stateRoot = ?",
        (rs, rowNum) -> new SlotAndBlockRoot(getUInt64(rs, "slot"), getBytes32(rs, "blockRoot")),
        (Object) stateRoot.toArrayUnsafe());
  }

  public Optional<BeaconState> getLatestFinalizedState() {
    return loadSingle(
        "SELECT ssz FROM finalized_state", (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class));
  }

  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return loadSingle(
        "SELECT ssz FROM state WHERE slot <= ? AND ssz IS NOT NULL ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getSsz(rs, BeaconStateImpl.class),
        maxSlot.bigIntegerValue());
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return getBlockByBlockRoot(blockRoot, false);
  }

  public Optional<SignedBeaconBlock> getBlockByBlockRoot(
      final Bytes32 blockRoot, final boolean onlyNonFinalized) {
    String sql = "SELECT ssz FROM block WHERE blockRoot = ?";
    if (onlyNonFinalized) {
      sql += " AND finalized == 0";
    }
    return loadSingle(
        sql,
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        (Object) blockRoot.toArrayUnsafe());
  }

  private <T> T getSsz(final ResultSet rs, final Class<? extends T> type) throws SQLException {
    final byte[] rawData = rs.getBytes("ssz");
    if (rawData == null) {
      return null;
    }
    try {
      final Bytes data = Bytes.wrap(compress ? Snappy.uncompress(rawData) : rawData);
      return SimpleOffsetSerializer.deserialize(data, type);
    } catch (IOException e) {
      throw new SQLException("Failed to read BLOB content", e);
    }
  }

  public Optional<SignedBeaconBlock> getFinalizedBlockBySlot(final UInt64 slot) {
    return loadSingle(
        "SELECT ssz FROM block WHERE slot = ? AND finalized = true",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return loadSingle(
        "SELECT ssz FROM block WHERE slot <= ? AND finalized = true ORDER BY slot DESC LIMIT 1",
        (rs, rowNum) -> getSsz(rs, SignedBeaconBlock.class),
        slot.bigIntegerValue());
  }

  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    final List<Bytes32> blockRoots =
        jdbc.query(
            "SELECT blockRoot FROM block WHERE finalized = true AND slot >= ? AND slot <= ? ORDER BY slot",
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

  public Map<Bytes32, UInt64> getBlockRootToSlotLookup() {
    final Map<Bytes32, UInt64> rootToSlotLookup = new HashMap<>();
    loadForEach(
        "SELECT blockRoot, slot FROM block WHERE finalized = false",
        rs -> rootToSlotLookup.put(getBytes32(rs, "blockRoot"), getUInt64(rs, "slot")));
    return rootToSlotLookup;
  }

  public Map<UInt64, VoteTracker> loadVotes() {
    final Map<UInt64, VoteTracker> votes = new HashMap<>();
    loadForEach(
        "SELECT validatorIndex, currentRoot, nextRoot, nextEpoch from vote",
        rs ->
            votes.put(
                getUInt64(rs, "validatorIndex"),
                new VoteTracker(
                    getBytes32(rs, "currentRoot"),
                    getBytes32(rs, "nextRoot"),
                    getUInt64(rs, "nextEpoch"))));
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

  private UInt64 getUInt64(final ResultSet rs, final String field) throws SQLException {
    return UInt64.valueOf(rs.getBigDecimal(field).toBigIntegerExact());
  }

  private Bytes32 getBytes32(final ResultSet rs, final String field) throws SQLException {
    return Bytes32.wrap(rs.getBytes(field));
  }

  private Bytes getBytes(final ResultSet rs, final String field) throws SQLException {
    return Bytes.wrap(rs.getBytes(field));
  }

  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return loadSingle(
        "SELECT block_timestamp, block_number, block_hash FROM eth1_min_genesis",
        (rs, rowNum) ->
            new MinGenesisTimeBlockEvent(
                getUInt64(rs, "block_timestamp"),
                getUInt64(rs, "block_number"),
                getBytes32(rs, "block_hash")));
  }

  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    // TODO: Should load these in pages
    final List<BlockInfo> blocks =
        jdbc.query(
            "SELECT block_number, block_timestamp, block_hash FROM eth1_deposit_block",
            (rs, rowNum) ->
                new BlockInfo(
                    getUInt64(rs, "block_number"),
                    getUInt64(rs, "block_timestamp"),
                    getBytes32(rs, "block_hash")));
    return blocks.stream()
        .map(
            block ->
                DepositsFromBlockEvent.create(
                    block.blockNumber,
                    block.blockHash,
                    block.blockTimestamp,
                    loadDeposits(block).stream()));
  }

  private List<Deposit> loadDeposits(final BlockInfo block) {
    return jdbc.query(
        "SELECT * FROM eth1_deposit WHERE block_number = ? ORDER BY merkle_tree_index",
        (rs, rowNum) ->
            new Deposit(
                BLSPublicKey.fromBytesCompressed(Bytes48.wrap(getBytes(rs, "public_key"))),
                getBytes32(rs, "withdrawal_credentials"),
                BLSSignature.fromBytesCompressed(getBytes(rs, "signature")),
                getUInt64(rs, "amount"),
                getUInt64(rs, "merkle_tree_index")),
        block.blockNumber);
  }

  private static class BlockInfo {
    private final UInt64 blockNumber;
    private final UInt64 blockTimestamp;
    private final Bytes32 blockHash;

    private BlockInfo(
        final UInt64 blockNumber, final UInt64 blockTimestamp, final Bytes32 blockHash) {
      this.blockNumber = blockNumber;
      this.blockTimestamp = blockTimestamp;
      this.blockHash = blockHash;
    }
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

    public void storeWeakSubjectivityCheckpoint(final Checkpoint checkpoint) {
      setCheckpoint(CheckpointType.WEAK_SUBJECTIVITY, checkpoint);
    }

    public void clearWeakSubjectivityCheckpoint() {
      execSql("DELETE FROM checkpoint WHERE type = ?", CheckpointType.WEAK_SUBJECTIVITY.name());
    }

    private void setCheckpoint(final CheckpointType type, final Checkpoint checkpoint) {
      execSql(
          "INSERT INTO checkpoint (type, blockRoot, epoch) VALUES (?, ?, ?)"
              + " ON CONFLICT(type) DO UPDATE SET blockRoot = excluded.blockRoot, epoch = excluded.epoch",
          type.name(),
          checkpoint.getRoot().toArrayUnsafe(),
          checkpoint.getEpoch().bigIntegerValue());
    }

    public void storeBlock(final SignedBeaconBlock block, final boolean finalized) {
      execSql(
          "INSERT INTO block (blockRoot, slot, parentRoot, finalized, ssz) VALUES (?, ?, ?, ?, ?) "
              + " ON CONFLICT(blockRoot) DO UPDATE SET finalized = excluded.finalized",
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
          "INSERT INTO state (stateRoot, blockRoot, slot) VALUES (?, ?, ?)"
              + " ON CONFLICT(stateRoot) DO NOTHING",
          stateRoot.toArrayUnsafe(),
          slotAndBlockRoot.getBlockRoot().toArrayUnsafe(),
          slotAndBlockRoot.getSlot().bigIntegerValue());
    }

    public void storeState(final Bytes32 blockRoot, final BeaconState state) {
      execSql(
          "INSERT INTO state (stateRoot, blockRoot, slot, ssz) VALUES (?, ?, ?, ?)"
              + " ON CONFLICT(stateRoot) DO UPDATE SET ssz = excluded.ssz",
          state.hash_tree_root().toArrayUnsafe(),
          blockRoot.toArrayUnsafe(),
          state.getSlot().bigIntegerValue(),
          serializeSsz(state));
    }

    public void storeLatestFinalizedState(final BeaconState state) {
      execSql(
          "INSERT INTO finalized_state (id, ssz) VALUES (1, ?) "
              + "ON CONFLICT(id) DO UPDATE SET ssz = excluded.ssz",
          serializeSsz(state));
    }

    public void deleteStateByBlockRoot(final Bytes32 blockRoot) {
      execSql("DELETE FROM state WHERE blockRoot = ?", (Object) blockRoot.toArrayUnsafe());
    }

    public void storeVotes(final Map<UInt64, VoteTracker> votes) {
      votes.forEach(
          (validatorIndex, voteTracker) ->
              execSql(
                  "INSERT INTO vote (validatorIndex, currentRoot, nextRoot, nextEpoch) VALUES (?, ?, ?, ?)"
                      + " ON CONFLICT(validatorIndex) DO UPDATE SET"
                      + "                     currentRoot = excluded.currentRoot,"
                      + "                     nextRoot = excluded.nextRoot,"
                      + "                     nextEpoch = excluded.nextEpoch",
                  validatorIndex.bigIntegerValue(),
                  voteTracker.getCurrentRoot().toArrayUnsafe(),
                  voteTracker.getNextRoot().toArrayUnsafe(),
                  voteTracker.getNextEpoch().bigIntegerValue()));
    }

    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      execSql(
          "INSERT INTO eth1_min_genesis (id, block_timestamp, block_number, block_hash) "
              + " VALUES (1, ?, ?, ?)"
              + " ON CONFLICT(id) DO UPDATE SET block_timestamp = excluded.block_timestamp,"
              + "                               block_number = excluded.block_number,"
              + "                               block_hash = excluded.block_hash",
          event.getTimestamp().bigIntegerValue(),
          event.getBlockNumber().bigIntegerValue(),
          event.getBlockHash().toArrayUnsafe());
    }

    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      execSql(
          "INSERT INTO eth1_deposit_block (block_number, block_timestamp, block_hash) "
              + "   VALUES (?, ?, ?) ",
          event.getBlockNumber().bigIntegerValue(),
          event.getBlockTimestamp().bigIntegerValue(),
          event.getBlockHash().toArrayUnsafe());
      event
          .getDeposits()
          .forEach(
              deposit ->
                  execSql(
                      "INSERT INTO eth1_deposit "
                          + "(merkle_tree_index, block_number, public_key, withdrawal_credentials, signature, amount) "
                          + "VALUES (?, ?, ?, ?, ?, ?)",
                      deposit.getMerkle_tree_index().bigIntegerValue(),
                      event.getBlockNumber().bigIntegerValue(),
                      deposit.getPubkey().toBytesCompressed().toArrayUnsafe(),
                      deposit.getWithdrawal_credentials().toArrayUnsafe(),
                      deposit.getSignature().toBytesCompressed().toArrayUnsafe(),
                      deposit.getAmount().bigIntegerValue()));
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
