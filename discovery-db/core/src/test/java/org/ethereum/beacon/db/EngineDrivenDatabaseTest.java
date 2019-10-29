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

package org.ethereum.beacon.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nonnull;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.StorageEngineSource;
import org.ethereum.beacon.db.source.impl.MemSizeEvaluators;
import org.junit.Ignore;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt64;

public class EngineDrivenDatabaseTest {

  @Test
  public void generalCasesAreCorrect() {
    TestStorageSource engineSource = new TestStorageSource();
    EngineDrivenDatabase db = EngineDrivenDatabase.createWithInstantFlusher(engineSource);

    DataSource<BytesValue, BytesValue> storage = db.createStorage("test");

    storage.put(wrap("ONE"), wrap("FIRST"));
    storage.put(wrap("TWO"), wrap("SECOND"));

    assertTrue(engineSource.source.isEmpty());
    assertEquals(wrap("FIRST"), storage.get(wrap("ONE")).get());
    assertEquals(wrap("SECOND"), storage.get(wrap("TWO")).get());
    assertFalse(storage.get(wrap("THREE")).isPresent());

    db.commit();

    assertFalse(db.getWriteBuffer().getCacheEntry(wrap("ONE")).isPresent());
    assertFalse(db.getWriteBuffer().getCacheEntry(wrap("TWO")).isPresent());
    assertEquals(0L, db.getWriteBuffer().evaluateSize());

    assertTrue(engineSource.source.containsValue(wrap("FIRST")));
    assertTrue(engineSource.source.containsValue(wrap("SECOND")));
    assertEquals(wrap("FIRST"), storage.get(wrap("ONE")).get());
    assertEquals(wrap("SECOND"), storage.get(wrap("TWO")).get());
    assertFalse(storage.get(wrap("THREE")).isPresent());

    storage.remove(wrap("SECOND"));
    storage.put(wrap("THREE"), wrap("THIRD"));
    storage.remove(wrap("TWO"));

    assertTrue(engineSource.source.containsValue(wrap("FIRST")));
    assertTrue(engineSource.source.containsValue(wrap("SECOND")));
    assertFalse(engineSource.source.containsValue(wrap("THIRD")));

    assertEquals(wrap("FIRST"), storage.get(wrap("ONE")).get());
    assertFalse(storage.get(wrap("TWO")).isPresent());
    assertEquals(wrap("THIRD"), storage.get(wrap("THREE")).get());

    db.commit();

    assertTrue(engineSource.source.containsValue(wrap("FIRST")));
    assertFalse(engineSource.source.containsValue(wrap("SECOND")));
    assertTrue(engineSource.source.containsValue(wrap("THIRD")));

    assertEquals(wrap("FIRST"), storage.get(wrap("ONE")).get());
    assertFalse(storage.get(wrap("TWO")).isPresent());
    assertEquals(wrap("THIRD"), storage.get(wrap("THREE")).get());
  }

  @Test
  public void multipleStorageCase() {
    TestStorageSource engineSource = new TestStorageSource();
    EngineDrivenDatabase db = EngineDrivenDatabase.createWithInstantFlusher(engineSource);

    DataSource<BytesValue, BytesValue> uno = db.createStorage("uno");
    DataSource<BytesValue, BytesValue> dos = db.createStorage("dos");

    uno.put(wrap("ONE"), wrap("FIRST"));
    uno.put(wrap("TWO"), wrap("SECOND"));
    dos.put(wrap("TWO"), wrap("SECOND"));
    uno.put(wrap("THREE"), wrap("UNO_THIRD"));
    dos.put(wrap("FOUR"), wrap("DOS_FOURTH"));

    db.commit();
    assertEquals(wrap("FIRST"), uno.get(wrap("ONE")).get());
    assertEquals(wrap("SECOND"), uno.get(wrap("TWO")).get());
    assertEquals(wrap("SECOND"), dos.get(wrap("TWO")).get());
    assertEquals(wrap("UNO_THIRD"), uno.get(wrap("THREE")).get());
    assertEquals(wrap("DOS_FOURTH"), dos.get(wrap("FOUR")).get());
    assertFalse(uno.get(wrap("FOUR")).isPresent());
    assertFalse(dos.get(wrap("ONE")).isPresent());
    assertFalse(dos.get(wrap("THREE")).isPresent());

    uno.remove(wrap("TWO"));
    dos.put(wrap("THREE"), wrap("DOS_THIRD"));

    assertFalse(uno.get(wrap("TWO")).isPresent());
    assertEquals(wrap("DOS_THIRD"), dos.get(wrap("THREE")).get());
    assertEquals(wrap("UNO_THIRD"), uno.get(wrap("THREE")).get());

    db.commit();
    assertFalse(uno.get(wrap("TWO")).isPresent());
    assertEquals(wrap("DOS_THIRD"), dos.get(wrap("THREE")).get());
    assertEquals(wrap("UNO_THIRD"), uno.get(wrap("THREE")).get());

    dos.remove(wrap("FOUR"));
    uno.put(wrap("FOUR"), wrap("UNO_FOURTH"));
    assertEquals(wrap("UNO_FOURTH"), uno.get(wrap("FOUR")).get());
    assertFalse(dos.get(wrap("FOUR")).isPresent());

    db.commit();
    assertEquals(wrap("UNO_FOURTH"), uno.get(wrap("FOUR")).get());
    assertFalse(dos.get(wrap("FOUR")).isPresent());
  }

  @Test
  public void checkBufferSizeFlusher() {
    TestStorageSource engineSource = new TestStorageSource();
    EngineDrivenDatabase db = EngineDrivenDatabase.create(engineSource, 512);

    Random rnd = new Random();

    DataSource<BytesValue, BytesValue> storage = db.createStorage("test");
    storage.put(wrap("ONE"), Bytes32.random(rnd));
    storage.put(wrap("TWO"), Bytes32.random(rnd));

    db.commit();

    // no flush is expected
    assertTrue(engineSource.source.isEmpty());

    storage.put(wrap("THREE"), Bytes32.random(rnd));
    storage.put(wrap("FOUR"), Bytes32.random(rnd));

    // should be flushed now
    db.commit();
    assertEquals(4, engineSource.source.size());
    assertEquals(0L, db.getWriteBuffer().evaluateSize());
    assertFalse(db.getWriteBuffer().getCacheEntry(wrap("ONE")).isPresent());
    assertFalse(db.getWriteBuffer().getCacheEntry(wrap("TWO")).isPresent());
    assertFalse(db.getWriteBuffer().getCacheEntry(wrap("THREE")).isPresent());
    assertFalse(db.getWriteBuffer().getCacheEntry(wrap("FOUR")).isPresent());

    storage.put(wrap("FIVE"), Bytes32.random(rnd));
    storage.put(wrap("SIX"), Bytes32.random(rnd));
    assertEquals(
        4 * MemSizeEvaluators.BytesValueEvaluator.apply(Bytes32.random(rnd)),
        db.getWriteBuffer().evaluateSize());

    storage.remove(wrap("FIVE"));

    assertEquals(
        2 * MemSizeEvaluators.BytesValueEvaluator.apply(Bytes32.random(rnd)),
        db.getWriteBuffer().evaluateSize());
  }

  @Test
  @Ignore
  public void checkWithConcurrentAccessTake1() throws InterruptedException {
    TestStorageSource engineSource = new TestStorageSource();
    EngineDrivenDatabase db = EngineDrivenDatabase.createWithInstantFlusher(engineSource);

    DataSource<BytesValue, BytesValue> one = db.createStorage("one");
    DataSource<BytesValue, BytesValue> two = db.createStorage("two");

    Map<BytesValue, BytesValue> writtenToOne = Collections.synchronizedMap(new HashMap<>());
    Map<BytesValue, BytesValue> writtenToTwo = Collections.synchronizedMap(new HashMap<>());

    Thread w1 = spawnWriterThread(1, one, writtenToOne);
    Thread w2 = spawnWriterThread(2, one, writtenToOne);
    Thread r1 = spawnReaderThread(3, one);
    Thread r2 = spawnReaderThread(4, one);

    Thread w3 = spawnWriterThread(5, two, writtenToTwo);
    Thread w4 = spawnWriterThread(6, two, writtenToTwo);
    Thread r3 = spawnReaderThread(7, two);
    Thread r4 = spawnReaderThread(8, two);

    List<Thread> threads = Arrays.asList(w1, w2, w3, w4, r1, r2, r3, r4);
    threads.forEach(Thread::start);

    Random rnd = new Random();
    for (int i = 0; i < 10; i++) {
      Thread.sleep(Math.abs(rnd.nextLong() % 1000));
      db.commit();
    }

    for (Thread t : threads) {
      t.interrupt();
      t.join();
    }

    db.commit();

    Set<BytesValue> sourceValues = new HashSet<>(engineSource.source.values());
    Set<BytesValue> expectedValues = new HashSet<>(writtenToOne.values());
    expectedValues.addAll(writtenToTwo.values());

    assertEquals(expectedValues, sourceValues);
  }

  @Test
  @Ignore
  public void checkWithConcurrentAccessTake2() throws InterruptedException {
    TestStorageSource engineSource = new TestStorageSource();
    EngineDrivenDatabase db = EngineDrivenDatabase.createWithInstantFlusher(engineSource);

    DataSource<BytesValue, BytesValue> one = db.createStorage("one");
    DataSource<BytesValue, BytesValue> two = db.createStorage("two");

    Map<BytesValue, BytesValue> writtenToOne = Collections.synchronizedMap(new HashMap<>());
    Map<BytesValue, BytesValue> writtenToTwo = Collections.synchronizedMap(new HashMap<>());

    Thread w1 = spawnWriterThread(1, one, writtenToOne);
    Thread w2 = spawnWriterThread(2, one, writtenToOne);
    Thread m1 = spawnModifierThread(3, one);
    Thread m2 = spawnModifierThread(4, one);

    Thread w3 = spawnWriterThread(5, two, writtenToTwo);
    Thread w4 = spawnWriterThread(6, two, writtenToTwo);
    Thread m3 = spawnModifierThread(7, two);
    Thread m4 = spawnModifierThread(8, two);

    List<Thread> threads = Arrays.asList(w1, w2, w3, w4, m1, m2, m3, m4);
    threads.forEach(Thread::start);

    Random rnd = new Random();
    for (int i = 0; i < 10; i++) {
      Thread.sleep(Math.abs(rnd.nextLong() % 1000));
      db.commit();
    }

    for (Thread t : threads) {
      t.interrupt();
      t.join();
    }

    db.commit();

    Set<BytesValue> sourceValues = new HashSet<>(engineSource.source.values());
    Set<BytesValue> expectedValues = new HashSet<>(writtenToOne.values());
    expectedValues.addAll(writtenToTwo.values());

    assertTrue(expectedValues.size() >= sourceValues.size());
  }

  private Thread spawnWriterThread(
      long id,
      DataSource<BytesValue, BytesValue> source,
      Map<BytesValue, BytesValue> writtenValues) {
    Random rnd = new Random(id);
    return new Thread(
        () -> {
          long writesTotal = 0;
          while (!Thread.currentThread().isInterrupted()) {
            long key = rnd.nextLong() % 1000;
            Bytes32 value = Bytes32.random(rnd);
            source.put(UInt64.valueOf(key).toBytes8(), value);
            writtenValues.put(UInt64.valueOf(key).toBytes8(), value);

            writesTotal += 1;

            try {
              Thread.sleep(Math.abs(rnd.nextLong() % 10));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          System.out.println(String.format("Writer %d: writes %d", id, writesTotal));
        });
  }

  private Thread spawnReaderThread(long id, DataSource<BytesValue, BytesValue> source) {
    Random rnd = new Random(id);
    return new Thread(
        () -> {
          long readsTotal = 0;
          long readsSuccessful = 0;

          while (!Thread.currentThread().isInterrupted()) {
            long key = rnd.nextLong() % 1000;
            Optional<BytesValue> val = source.get(UInt64.valueOf(key).toBytes8());
            readsTotal += 1;
            if (val.isPresent()) {
              readsSuccessful += 1;
            }

            try {
              Thread.sleep(Math.abs(rnd.nextLong() % 10));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          System.out.println(
              String.format("Reader %d: reads %d, non null %d", id, readsTotal, readsSuccessful));
        });
  }

  private Thread spawnModifierThread(long id, DataSource<BytesValue, BytesValue> source) {
    Random rnd = new Random(id);
    return new Thread(
        () -> {
          long readsTotal = 0;
          long readsSuccessful = 0;
          long removalsTotal = 0;

          while (!Thread.currentThread().isInterrupted()) {
            long key = rnd.nextLong() % 1000;
            Optional<BytesValue> val = source.get(UInt64.valueOf(key).toBytes8());
            readsTotal += 1;
            if (val.isPresent()) {
              readsSuccessful += 1;
              if (Math.abs(rnd.nextLong() % 100) < 20) {
                removalsTotal += 1;
                source.remove(UInt64.valueOf(key).toBytes8());
              }
            }

            try {
              Thread.sleep(Math.abs(rnd.nextLong() % 10));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }

          System.out.println(
              String.format(
                  "Modifier %d: reads %d, non null %d, removals %d",
                  id, readsTotal, readsSuccessful, removalsTotal));
        });
  }

  private BytesValue wrap(String value) {
    return BytesValue.wrap(value.getBytes());
  }

  private static class TestStorageSource implements StorageEngineSource<BytesValue> {

    private final HashMap<BytesValue, BytesValue> source = new HashMap<>();

    @Override
    public void open() {}

    @Override
    public void close() {}

    @Override
    public void batchUpdate(Map<BytesValue, BytesValue> updates) {
      source.putAll(updates);
    }

    @Override
    public Optional<BytesValue> get(@Nonnull BytesValue key) {
      return Optional.ofNullable(source.get(key));
    }

    @Override
    public void put(@Nonnull BytesValue key, @Nonnull BytesValue value) {
      source.put(key, value);
    }

    @Override
    public void remove(@Nonnull BytesValue key) {
      source.remove(key);
    }

    @Override
    public void flush() {}
  }
}
