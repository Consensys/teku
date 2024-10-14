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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

@SuppressWarnings("FutureReturnValueIgnored")
public class AsyncStreamPublisherTest {

  AsyncStreamPublisher<Integer> publisher = AsyncStream.createPublisher(Integer.MAX_VALUE);

  @Test
  void sanityTest() {
    ArrayList<Integer> collector = new ArrayList<>();

    SafeFuture<List<Integer>> listPromise =
        publisher
            .flatMap(
                i -> AsyncStream.create(IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
            .filter(i -> i % 2 == 0)
            .map(i -> i * 10)
            .limit(10)
            .collect(collector);

    assertThat(collector).isEmpty();

    {
      SafeFuture<Boolean> f = publisher.onNext(0);
      assertThat(f).isCompletedWithValue(true);
    }
    assertThat(collector).containsExactly(0, 20, 40);

    {
      SafeFuture<Boolean> f = publisher.onNext(1);
      assertThat(f).isCompletedWithValue(true);
    }
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140);

    {
      SafeFuture<Boolean> f = publisher.onNext(2);
      assertThat(f).isCompletedWithValue(true);
    }
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240);
    assertThat(listPromise).isNotDone();

    {
      SafeFuture<Boolean> f = publisher.onNext(3);
      assertThat(f).isCompletedWithValue(false);
    }
    // limit(10) kicks in
    assertThat(collector).containsExactly(0, 20, 40, 100, 120, 140, 200, 220, 240, 300);
    assertThat(listPromise)
        .isCompletedWithValue(List.of(0, 20, 40, 100, 120, 140, 200, 220, 240, 300));
  }

  @Test
  void completeShouldCompleteStream() {
    SafeFuture<List<Integer>> listPromise =
        publisher
            .flatMap(
                i -> AsyncStream.create(IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
            .filter(i -> i % 2 == 0)
            .map(i -> i * 10)
            .toList();

    publisher.onNext(0);
    publisher.onComplete();
    assertThat(listPromise).isCompletedWithValue(List.of(0, 20, 40));
  }

  @Test
  void errorShouldCompleteStream() {
    SafeFuture<List<Integer>> listPromise =
        publisher
            .flatMap(
                i -> AsyncStream.create(IntStream.range(i * 10, i * 10 + 5).boxed().iterator()))
            .filter(i -> i % 2 == 0)
            .map(i -> i * 10)
            .toList();

    publisher.onNext(0);
    publisher.onError(new RuntimeException("test"));
    assertThat(listPromise).isCompletedExceptionally();
  }
}
