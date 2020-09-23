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

import static tech.pegasys.teku.storage.server.sql.ParamConverter.convertParams;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSqlStorage implements AutoCloseable {

  protected final PlatformTransactionManager transactionManager;
  protected final JdbcOperations jdbc;
  private final HikariDataSource dataSource;

  protected AbstractSqlStorage(
      PlatformTransactionManager transactionManager, final HikariDataSource dataSource) {
    this.transactionManager = transactionManager;
    this.dataSource = dataSource;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  protected <T> Optional<T> loadSingle(
      final String sql, final RowMapper<T> mapper, final Object... params) {
    try {
      return Optional.ofNullable(jdbc.queryForObject(sql, mapper, convertParams(params)));
    } catch (final EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  protected <K, V> Map<K, V> loadMap(
      final String sql,
      final SqlFunction<K> keyExtractor,
      final SqlFunction<V> valueExtractor,
      final Object... params) {
    final Map<K, V> result = new HashMap<>();
    loadForEach(sql, rs -> result.put(keyExtractor.apply(rs), valueExtractor.apply(rs)), params);
    return result;
  }

  protected void loadForEach(
      final String sql, final RowCallbackHandler rowHandler, final Object... params) {
    jdbc.query(sql, rowHandler, convertParams(params));
  }

  protected UInt64 getUInt64(final ResultSet rs, final String field) throws SQLException {
    return UInt64.valueOf(rs.getBigDecimal(field).toBigIntegerExact());
  }

  protected Bytes32 getBytes32(final ResultSet rs, final String field) throws SQLException {
    return Bytes32.wrap(rs.getBytes(field));
  }

  protected Bytes getBytes(final ResultSet rs, final String field) throws SQLException {
    return Bytes.wrap(rs.getBytes(field));
  }

  protected <T> T getSsz(final ResultSet rs, final Class<? extends T> type) throws SQLException {
    final byte[] rawData = rs.getBytes("ssz");
    if (rawData == null) {
      return null;
    }
    return SimpleOffsetSerializer.deserialize(Bytes.wrap(rawData), type);
  }

  protected Object writeBytes(final Bytes value) {
    return value.toArrayUnsafe();
  }

  @Override
  public void close() {
    dataSource.close();
  }

  @FunctionalInterface
  protected interface SqlFunction<O> {
    O apply(ResultSet rs) throws SQLException;
  }
}
