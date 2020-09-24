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

import static java.util.Spliterators.spliteratorUnknownSize;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

public class SqlStream<T> implements Iterator<T> {
  private final JdbcOperations jdbc;
  private final int batchSize;
  private final String query;
  private final RowMapper<T> rowMapper;
  private final Object[] params;

  private Iterator<T> currentBatch = Collections.emptyIterator();
  private int offset = 0;
  private boolean expectMoreBatches = true;

  private SqlStream(
      final JdbcOperations jdbc,
      final int batchSize,
      final String query,
      final RowMapper<T> rowMapper,
      final Object... params) {
    this.jdbc = jdbc;
    this.batchSize = batchSize;
    this.query = query;
    this.rowMapper = rowMapper;
    this.params = Arrays.copyOf(params, params.length + 2);
  }

  public static <T> Stream<T> stream(
      final JdbcOperations jdbc,
      final int batchSize,
      final String query,
      final RowMapper<T> rowMapper,
      final Object... params) {
    final SqlStream<T> iterator = new SqlStream<>(jdbc, batchSize, query, rowMapper, params);
    return StreamSupport.stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
  }

  @Override
  public boolean hasNext() {
    if (currentBatch.hasNext()) {
      return true;
    }
    if (!expectMoreBatches) {
      return false;
    }
    currentBatch = loadNextBatch();
    return currentBatch.hasNext();
  }

  private Iterator<T> loadNextBatch() {
    params[params.length - 2] = batchSize;
    params[params.length - 1] = offset;
    final List<T> nextBatch = jdbc.query(query + " LIMIT ? OFFSET ? ", rowMapper, params);
    if (nextBatch.size() < batchSize) {
      // We didn't fill this batch so there can't be any more items remaining
      expectMoreBatches = false;
    }
    offset += batchSize;
    return nextBatch.iterator();
  }

  @Override
  public T next() {
    return currentBatch.next();
  }
}
