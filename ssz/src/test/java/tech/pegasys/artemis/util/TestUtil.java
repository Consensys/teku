package tech.pegasys.artemis.util;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestUtil {

  public static <C> List<C> waitAll(List<Future<C>> futures) {
    return futures.stream()
        .map(
            f -> {
              try {
                return f.get();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  public static <C> List<Future<C>> executeParallel(Callable<C> task, int threadNum) {
    ExecutorService threadPool = Executors.newFixedThreadPool(threadNum);
    CountDownLatch latch = new CountDownLatch(threadNum);
    try {
      return IntStream.range(0, threadNum)
          .mapToObj(
              i ->
                  (Callable<C>)
                      () -> {
                        latch.countDown();
                        // start simultaneously for more aggressive concurrency
                        latch.await();
                        return task.call();
                      })
          .map(threadPool::submit)
          .collect(Collectors.toList());
    } finally {
      threadPool.shutdown();
    }
  }
}
