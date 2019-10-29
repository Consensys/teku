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

import java.io.IOException;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class RocksDbDrivenDatabaseTest {

  @After
  @Before
  public void cleanUp() throws IOException {
    FileUtil.removeRecursively("test-db");
  }

  @Test
  public void basicOperations() {
    Database db = Database.rocksDB("test-db", -1);

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
    assertFalse(uno.get(wrap("FOUR")).isPresent());

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

    db.close();
  }

  @Test
  public void reopenWithoutFlush() {
    Database db = Database.rocksDB("test-db", -1);

    DataSource<BytesValue, BytesValue> storage = db.createStorage("uno");

    storage.put(wrap("ONE"), wrap("FIRST"));
    storage.put(wrap("TWO"), wrap("SECOND"));
    storage.put(wrap("THREE"), wrap("THIRD"));

    db.close();

    storage = db.createStorage("uno");

    assertEquals(wrap("FIRST"), storage.get(wrap("ONE")).get());
    assertEquals(wrap("SECOND"), storage.get(wrap("TWO")).get());
    assertEquals(wrap("THIRD"), storage.get(wrap("THREE")).get());

    db.close();
  }

  private BytesValue wrap(String value) {
    return BytesValue.wrap(value.getBytes());
  }
}
