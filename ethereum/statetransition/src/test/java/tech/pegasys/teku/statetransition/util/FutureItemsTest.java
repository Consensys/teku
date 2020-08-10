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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class FutureItemsTest {

  private final FutureItems<Item> futureItems = new FutureItems<>(Item::getSlot);

  @Test
  public void add() {
    final Item item = new Item(5);

    futureItems.add(item);
    assertThat(futureItems.size()).isEqualTo(1);
    assertThat(futureItems.contains(item)).isTrue();
  }

  @Test
  public void prune_nothingToPrune() {
    final Item item = new Item(5);

    futureItems.add(item);

    final UInt64 priorSlot = item.getSlot().minus(UInt64.ONE);
    final List<Item> pruned = futureItems.prune(priorSlot);
    assertThat(pruned).isEmpty();

    assertThat(futureItems.size()).isEqualTo(1);
    assertThat(futureItems.contains(item)).isTrue();
  }

  @Test
  public void prune_itemAtSlot() {
    final Item item = new Item(5);

    futureItems.add(item);

    final List<Item> pruned = futureItems.prune(item.getSlot());
    assertThat(pruned).containsExactly(item);
    assertThat(futureItems.size()).isEqualTo(0);
  }

  @Test
  public void prune_itemPriorToSlot() {
    final Item item = new Item(5);

    futureItems.add(item);

    final List<Item> pruned = futureItems.prune(item.getSlot().plus(UInt64.ONE));
    assertThat(pruned).containsExactly(item);
    assertThat(futureItems.size()).isEqualTo(0);
  }

  private static class Item {
    private final UInt64 slot;

    private Item(final long slot) {
      this.slot = UInt64.valueOf(slot);
    }

    public UInt64 getSlot() {
      return slot;
    }
  }
}
