/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.javatuples.Pair;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RxUtil {

  public interface Function3<P1, P2, P3, R> {
    R apply(P1 p1, P2 p2, P3 p3);
  }

  public interface Function4<P1, P2, P3, P4, R> {
    R apply(P1 p1, P2 p2, P3 p3, P4 p4);
  }

  public interface Function5<P1, P2, P3, P4, P5, R> {
    R apply(P1 p1, P2 p2, P3 p3, P4 p4, P5 p5);
  }

  public static <T> Publisher<T> join(Publisher<T> s1, Publisher<T> s2, int bufferLen) {
    throw new UnsupportedOperationException();
  }

  enum Op {
    ADDED,
    REMOVED
  }

  public static <T> Flux<List<T>> collect(Publisher<T> addedStream, Publisher<T> removedStream) {
    return Flux.merge(
            Flux.from(addedStream).map(e -> Pair.with(Op.ADDED, e)),
            Flux.from(removedStream).map(e -> Pair.with(Op.REMOVED, e)))
        .scan(
            new ArrayList<T>(),
            (arr, op) -> {
              ArrayList<T> ret = new ArrayList<>(arr);
              if (op.getValue0() == Op.ADDED) {
                ret.add(op.getValue1());
              } else {
                ret.remove(op.getValue1());
              }
              return ret;
            });
  }

  public static <T> Mono<T> fromOptional(Optional<T> opt) {
    return opt.map(Mono::just).orElse(Mono.empty());
  }

  public static <P1, P2, P3, R> Flux<R> combineLatest(
      Publisher<P1> p1, Publisher<P2> p2, Publisher<P3> p3, Function3<P1, P2, P3, R> combiner) {

    return Flux.combineLatest(a -> combiner.apply((P1) a[0], (P2) a[1], (P3) a[2]), p1, p2, p3);
  }

  public static <P1, P2, P3, P4, R> Flux<R> combineLatest(
      Publisher<P1> p1,
      Publisher<P2> p2,
      Publisher<P3> p3,
      Publisher<P4> p4,
      Function4<P1, P2, P3, P4, R> combiner) {

    return Flux.combineLatest(
        a -> combiner.apply((P1) a[0], (P2) a[1], (P3) a[2], (P4) a[3]), p1, p2, p3, p4);
  }

  public static <P1, P2, P3, P4, P5, R> Flux<R> combineLatest(
      Publisher<P1> p1,
      Publisher<P2> p2,
      Publisher<P3> p3,
      Publisher<P4> p4,
      Publisher<P5> p5,
      Function5<P1, P2, P3, P4, P5, R> combiner) {

    return Flux.combineLatest(
        a -> combiner.apply((P1) a[0], (P2) a[1], (P3) a[2], (P4) a[3], (P5) a[4]),
        p1,
        p2,
        p3,
        p4,
        p5);
  }
}
