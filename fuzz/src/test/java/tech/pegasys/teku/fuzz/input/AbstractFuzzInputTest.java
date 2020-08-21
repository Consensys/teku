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

package tech.pegasys.teku.fuzz.input;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.fuzz.FuzzUtil;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public abstract class AbstractFuzzInputTest<T extends SimpleOffsetSerializable> {

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @BeforeEach
  public void setup() {
    FuzzUtil.initialize(false, true);
  }

  @Test
  public void serialize_roundTrip() {
    T original = createInput();

    final Bytes serialized = SimpleOffsetSerializer.serialize(original);
    T deserialized = SimpleOffsetSerializer.deserialize(serialized, getInputType());
    assertThat(deserialized).isEqualTo(original);
  }

  protected abstract Class<T> getInputType();

  protected abstract T createInput();
}
