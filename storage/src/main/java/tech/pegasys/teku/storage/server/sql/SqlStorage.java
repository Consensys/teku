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

import com.google.errorprone.annotations.MustBeClosed;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;

public class SqlStorage implements AutoCloseable {

  private final PlatformTransactionManager transactionManager;
  private final HikariDataSource dataSource;
  private final JdbcOperations jdbc;

  public SqlStorage(
      PlatformTransactionManager transactionManager, final HikariDataSource dataSource) {
    this.transactionManager = transactionManager;
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  @MustBeClosed
  public Transaction startTransaction() {
    return new Transaction(transactionManager.getTransaction(TransactionDefinition.withDefaults()));
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
