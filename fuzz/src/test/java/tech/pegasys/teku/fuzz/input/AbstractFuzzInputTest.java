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
import tech.pegasys.teku.fuzz.FuzzUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public abstract class AbstractFuzzInputTest<T extends SszData> {

  protected final Spec spec = SpecFactory.createMinimal();
  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @BeforeEach
  public void setup() {
    FuzzUtil.initialize(false, true);
  }

  @Test
  public void serialize_roundTrip() {
    T original = createInput();

    final Bytes serialized = original.sszSerialize();
    T deserialized = getInputType().sszDeserialize(serialized);
    assertThat(deserialized).isEqualTo(original);
  }

  protected abstract SszSchema<T> getInputType();

  protected abstract T createInput();
}
