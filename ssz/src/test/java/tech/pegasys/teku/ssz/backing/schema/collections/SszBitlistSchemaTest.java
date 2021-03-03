/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.schema.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.collections.SszBitlist;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;

public class SszBitlistSchemaTest {

  @Test
  void create_shouldCreateEmptySchema() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(0);
    assertThat(schema.getMaxLength()).isZero();
    SszBitlist empty = schema.empty();
    assertThat(empty.size()).isZero();
  }

  @Test
  void ofBits_shouldThrowIfSizeGreaterThenMaxLength() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(100);
    assertThatCode(() -> schema.ofBits(100)).doesNotThrowAnyException();
    assertThatThrownBy(() -> schema.ofBits(101)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ofBits_shouldThrowIfSizeNegative() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(100);
    assertThatThrownBy(() -> schema.ofBits(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ofBits_shouldThrowIfBitIndexIsNegative() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(100);
    assertThatThrownBy(() -> schema.ofBits(10, -1)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void ofBits_shouldThrowIfBitIndexGreaterThenSize() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(100);
    assertThatThrownBy(() -> schema.ofBits(50, 1, 2, 3, 50))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> schema.ofBits(100, 101)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void ofBits_shouldThrowIfMaxSizeNegative() {
    assertThatThrownBy(() -> SszBitlistSchema.create(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ofBits_test() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(300);
    assertThat(schema.ofBits(1).sszSerialize()).isEqualTo(Bytes.of(0b10));
    assertThat(schema.ofBits(1, 0).sszSerialize()).isEqualTo(Bytes.of(0b11));
    assertThat(schema.ofBits(2).sszSerialize()).isEqualTo(Bytes.of(0b100));
    assertThat(schema.ofBits(2, 0).sszSerialize()).isEqualTo(Bytes.of(0b101));
    assertThat(schema.ofBits(2, 1).sszSerialize()).isEqualTo(Bytes.of(0b110));
    assertThat(schema.ofBits(2, 0, 1).sszSerialize()).isEqualTo(Bytes.of(0b111));
    assertThat(schema.ofBits(300).size()).isEqualTo(300);
    assertThat(schema.ofBits(300).getAllSetBits()).isEmpty();
    assertThat(schema.ofBits(300, 299).getAllSetBits()).containsExactly(299);
    assertThat(schema.ofBits(300, IntStream.range(0, 300).toArray()).streamAllSetBits().distinct())
        .isSorted()
        .hasSize(300);
    assertThat(schema.ofBits(3, 0, 2)).isEqualTo(schema.ofBits(3, 2, 0));
  }

  @Test
  void createFromElements_shouldReturnSszBitlist() {
    SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(10);
    SszBitlist bitlist = schema.createFromElements(List.of(SszBit.of(false), SszBit.of(true)));
    assertThat(bitlist).isInstanceOf(SszBitlist.class);
  }

  @Test
  void testThatListSchemaCreatesBitlistSchemaImplementation() {
    SszListSchema<SszBit, ?> schema = SszListSchema.create(SszPrimitiveSchemas.BIT_SCHEMA, 10);
    assertThat(schema).isInstanceOf(SszBitlistSchema.class);
    assertThat(schema.getMaxLength()).isEqualTo(10);
    SszList<SszBit> sszList = schema.createFromElements(List.of(SszBit.of(false), SszBit.of(true)));
    assertThat(sszList).isInstanceOf(SszBitlist.class);
    SszBitlist sszBitlist = (SszBitlist) sszList;
    assertThat(sszBitlist.getSchema()).isEqualTo(schema);
    assertThat(sszBitlist.size()).isEqualTo(2);
    assertThat(sszBitlist.streamAllSetBits()).containsExactlyElementsOf(List.of(1));
  }
}
