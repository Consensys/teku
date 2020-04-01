package tech.pegasys.artemis.util.backing.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class ArrayIntCacheTest {

  @Test
  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  public void testThreadSafety() throws InterruptedException {
    ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    Thread t1 =
        new Thread(
            () -> {
              while (true) {
                String val = cache.get(1024, idx -> "aaa");
                assertThat(val).isEqualTo("aaa");
                cache.invalidateWithNewValueInt(1024, "aaa");
              }
            });
    t1.start();

    List<Thread> threads =
        IntStream.range(0, 16)
            .mapToObj(
                i ->
                    new Thread(
                        () -> {
                          while (true) {
                            IntCache<String> cache1 = cache.transfer();
                            String val = cache1.get(1024, idx -> "aaa");
                            assertThat(val).isEqualTo("aaa");
                            for (int j = 0; j < 100; j++) {
                              cache1.invalidateWithNewValueInt(1024, "bbb");
                              String val1 = cache1.get(1024, idx -> "bbb");
                              assertThat(val1).isEqualTo("bbb");
                            }
                          }
                        }))
            .peek(Thread::start)
            .collect(Collectors.toList());

    t1.join(1000);
    assertThat(t1.isAlive()).isTrue();
    assertThat(threads).allMatch(Thread::isAlive);
  }
}
