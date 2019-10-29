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

package org.ethereum.beacon.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

public class ConsumerListTest {
  @Test
  public void test() {
    final List<Integer> list = new ArrayList<>();
    Consumer<Integer> consumer = list::add;
    ConsumerList<Integer> consumerList = ConsumerList.create(5, consumer);
    assertEquals(0, consumerList.size());
    consumerList.add(1);
    consumerList.add(2);
    consumerList.add(3);
    consumerList.add(4);
    consumerList.add(5);
    consumerList.add(6);
    assertEquals(1, list.size());
    assertEquals(Integer.valueOf(1), list.get(0));
    assertEquals(5, consumerList.size());
    assertEquals(5, consumerList.maxSize);
    List<Integer> three = new ArrayList<>();
    three.add(7);
    three.add(8);
    three.add(9);
    consumerList.addAll(three);
    assertEquals(4, list.size());
    assertEquals(Integer.valueOf(2), list.get(1));
    assertEquals(Integer.valueOf(3), list.get(2));
    assertEquals(Integer.valueOf(4), list.get(3));
    assertTrue(consumerList.contains(5));
    assertTrue(consumerList.contains(6));
    assertTrue(consumerList.contains(7));
    assertTrue(consumerList.contains(8));
    assertTrue(consumerList.contains(9));
  }
}
