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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;

public class SqlEth1Storage extends AbstractSqlStorage {

  protected SqlEth1Storage(
      final PlatformTransactionManager transactionManager, final DataSource dataSource) {
    super(transactionManager, dataSource);
  }

  @MustBeClosed
  public SqlEth1Storage.Transaction startTransaction() {
    return new SqlEth1Storage.Transaction(
        transactionManager.getTransaction(TransactionDefinition.withDefaults()),
        transactionManager,
        jdbc);
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

    return SqlStream.stream(
            jdbc,
            50,
            "SELECT block_number, block_timestamp, block_hash FROM eth1_deposit_block ORDER BY block_number",
            (rs, rowNum) ->
                new BlockInfo(
                    getUInt64(rs, "block_number"),
                    getUInt64(rs, "block_timestamp"),
                    getBytes32(rs, "block_hash")))
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

  public static class Transaction extends AbstractSqlTransaction {

    protected Transaction(
        final TransactionStatus transaction,
        final PlatformTransactionManager transactionManager,
        final JdbcOperations jdbc) {
      super(transaction, transactionManager, jdbc);
    }

    public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      execSql(
          "INSERT INTO eth1_min_genesis (id, block_timestamp, block_number, block_hash) "
              + " VALUES (1, ?, ?, ?)"
              + " ON CONFLICT(id) DO UPDATE SET block_timestamp = excluded.block_timestamp,"
              + "                               block_number = excluded.block_number,"
              + "                               block_hash = excluded.block_hash",
          event.getTimestamp(),
          event.getBlockNumber(),
          event.getBlockHash());
    }

    public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {
      execSql(
          "INSERT INTO eth1_deposit_block (block_number, block_timestamp, block_hash) "
              + "VALUES (?, ?, ?) ",
          event.getBlockNumber(),
          event.getBlockTimestamp(),
          event.getBlockHash());
      event.getDeposits().forEach(deposit -> storeDeposit(event, deposit));
    }

    private void storeDeposit(final DepositsFromBlockEvent event, final Deposit deposit) {
      execSql(
          "INSERT INTO eth1_deposit "
              + "(merkle_tree_index, block_number, public_key, withdrawal_credentials, signature, amount) "
              + "VALUES (?, ?, ?, ?, ?, ?)",
          deposit.getMerkle_tree_index(),
          event.getBlockNumber(),
          deposit.getPubkey().toBytesCompressed(),
          deposit.getWithdrawal_credentials(),
          deposit.getSignature().toBytesCompressed(),
          deposit.getAmount());
    }
  }
}
