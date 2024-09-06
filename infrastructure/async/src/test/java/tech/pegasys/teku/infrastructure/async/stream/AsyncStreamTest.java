/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.async.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class AsyncStreamTest {

  @Test
  void sanityTest() {
    List<SafeFuture<Integer>> futures =
        Stream.generate(() -> new SafeFuture<Integer>()).limit(5).toList();

    ArrayList<Integer> collector = new ArrayList<>();

    SafeFuture<List<Integer>> listPromise =
        AsyncStream.create(futures.iterator())
            .flatMap(AsyncStream::create)
            .flatMap(
                i -> AsyncStream.create(IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
            .filter(i -> i % 2 == 0)
            .map(i -> i * 10)
            .limit(10)
            .collect(collector);

    assertThat(collector).isEmpty();

    futures.get(1).complete(1);

    assertThat(collector).isEmpty();

    futures.get(0).complete(0);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140);
    assertThat(listPromise).isNotDone();

    // even if future is completed exceptionally it never reaches downstream thus shouldn't affect
    // the final result
    futures.get(4).completeExceptionally(new RuntimeException("test"));

    assertThat(listPromise).isNotDone();

    futures.get(2).complete(2);

    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240);
    assertThat(listPromise).isNotDone();

    futures.get(3).complete(3);

    // limit(10) kicks in
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240, 300);
    assertThat(listPromise)
        .isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140, 200, 220, 240, 300));
  }

  @Test
  void longStreamOfCompletedFuturesShouldNotCauseStackOverflow() {
    List<Integer> ints =
        AsyncStream.create(IntStream.range(0, 10000).boxed().iterator())
            .mapAsync(SafeFuture::completedFuture)
            .toList()
            .join();

    assertThat(ints).hasSize(10000);
  }

  @Test
  void longStreamOfFlatMapShouldNotCauseStackOverflow() {
    List<Integer> ints =
        AsyncStream.create(IntStream.range(0, 10000).boxed().iterator())
            .flatMap(AsyncStream::of)
            .toList()
            .join();

    assertThat(ints).hasSize(10000);
  }

  @Test
  void checkTheOrderIsPreserved() {
    SafeFuture<Integer> fut0 = new SafeFuture<>();
    SafeFuture<Integer> fut1 = new SafeFuture<>();

    SafeFuture<List<Integer>> listFut = AsyncStream.of(fut0, fut1).mapAsync(f -> f).toList();

    fut1.complete(1);
    fut0.complete(0);

    assertThat(listFut).isCompletedWithValue(List.of(0, 1));
  }
}
