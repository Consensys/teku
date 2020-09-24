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

import static java.util.stream.Collectors.toList;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tech.pegasys.teku.protoarray.BlockInformation;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;

public class SqlProtoArrayStorage extends AbstractSqlStorage {

  protected SqlProtoArrayStorage(
      final PlatformTransactionManager transactionManager, final DataSource dataSource) {
    super(transactionManager, dataSource);
  }

  @MustBeClosed
  public SqlProtoArrayStorage.Transaction startTransaction() {
    return new SqlProtoArrayStorage.Transaction(
        transactionManager.getTransaction(TransactionDefinition.withDefaults()),
        transactionManager,
        jdbc);
  }

  public Optional<ProtoArraySnapshot> loadProtoArraySnapshot() {
    return loadSingle(
        "SELECT justifiedEpoch, finalizedEpoch, blocks FROM protoarray",
        (rs, rowNum) ->
            new ProtoArraySnapshot(
                getUInt64(rs, "justifiedEpoch"),
                getUInt64(rs, "finalizedEpoch"),
                getBlockInformation(getBytes(rs, "blocks"))));
  }

  private List<BlockInformation> getBlockInformation(final Bytes data) {
    return SSZ.decodeBytesList(data).stream().map(BlockInformation::fromBytes).collect(toList());
  }

  public static class Transaction extends AbstractSqlTransaction {

    protected Transaction(
        final TransactionStatus transaction,
        final PlatformTransactionManager transactionManager,
        final JdbcOperations jdbc) {
      super(transaction, transactionManager, jdbc);
    }

    public void storeProtoArraySnapshot(final ProtoArraySnapshot snapshot) {
      final Bytes blocks =
          SSZ.encodeBytesList(
              snapshot.getBlockInformationList().stream()
                  .map(BlockInformation::toBytes)
                  .toArray(Bytes[]::new));
      execSql(
          "INSERT INTO protoarray (id, justifiedEpoch, finalizedEpoch, blocks) VALUES (1, ?, ?, ?) "
              + "ON CONFLICT(id) DO UPDATE SET "
              + "    justifiedEpoch = excluded.justifiedEpoch, "
              + "    finalizedEpoch = excluded.finalizedEpoch, "
              + "    blocks = excluded.blocks",
          snapshot.getJustifiedEpoch(),
          snapshot.getFinalizedEpoch(),
          blocks);
    }
  }
}
