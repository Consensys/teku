/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.storage.server.rocksdb.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

public class UnsignedLongSerializerTest {

  private final UnsignedLongSerializer serializer = new UnsignedLongSerializer();

  @Test
  public void roundTrip_maxValue() {
    final UnsignedLong value = UnsignedLong.MAX_VALUE;
    final byte[] bytes = serializer.serialize(value);
    final UnsignedLong deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_zero() {
    final UnsignedLong value = UnsignedLong.ZERO;
    final byte[] bytes = serializer.serialize(value);
    final UnsignedLong deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_one() {
    final UnsignedLong value = UnsignedLong.ONE;
    final byte[] bytes = serializer.serialize(value);
    final UnsignedLong deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_other() {
    final UnsignedLong value = UnsignedLong.valueOf(12500L);
    final byte[] bytes = serializer.serialize(value);
    final UnsignedLong deserialized = serializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }
}
