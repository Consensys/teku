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

package org.ethereum.beacon.core.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

public class IteratorTest {

  @Test
  public void test1() {
    SlotNumber s1 = SlotNumber.of(10);
    SlotNumber s2 = SlotNumber.of(11);

    Iterator<SlotNumber> iterator = s1.iterateTo(s2).iterator();
    assertTrue(iterator.hasNext());
    assertEquals(SlotNumber.of(10), iterator.next());
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void test2() {
    SlotNumber s1 = SlotNumber.of(10);
    SlotNumber s2 = SlotNumber.of(10);

    Iterator<SlotNumber> iterator = s1.iterateTo(s2).iterator();
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  @Test
  public void test3() {
    SlotNumber s1 = SlotNumber.of(11);
    SlotNumber s2 = SlotNumber.of(10);

    Iterator<SlotNumber> iterator = s1.iterateTo(s2).iterator();
    assertFalse(iterator.hasNext());
    try {
      iterator.next();
      fail();
    } catch (NoSuchElementException e) {
      // expected
    }
  }
}
