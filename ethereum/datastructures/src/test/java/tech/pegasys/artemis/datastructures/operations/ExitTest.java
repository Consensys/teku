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

package tech.pegasys.artemis.datastructures.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import org.junit.jupiter.api.Test;

class ExitTest {

  @Test
  void equalsReturnsTrueWhenObjectAreSame() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong validatorIndex = randomUnsignedLong();
    List<Bytes48> signature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Exit e1 = new Exit(slot, validatorIndex, signature);
    Exit e2 = e1;

    assertEquals(e1, e2);
  }

  @Test
  void equalsReturnsTrueWhenObjectFieldsAreEqual() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong validatorIndex = randomUnsignedLong();
    List<Bytes48> signature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Exit e1 = new Exit(slot, validatorIndex, signature);
    Exit e2 = new Exit(slot, validatorIndex, signature);

    assertEquals(e1, e2);
  }

  @Test
  void equalsReturnsFalseWhenSlotsAreDifferent() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong validatorIndex = randomUnsignedLong();
    List<Bytes48> signature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Exit e1 = new Exit(slot, validatorIndex, signature);
    Exit e2 = new Exit(slot.plus(randomUnsignedLong()), validatorIndex, signature);

    assertNotEquals(e1, e2);
  }

  @Test
  void equalsReturnsFalseWhenValidatorIndicesAreDifferent() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong validatorIndex = randomUnsignedLong();
    List<Bytes48> signature = Arrays.asList(Bytes48.random(), Bytes48.random());

    Exit e1 = new Exit(slot, validatorIndex, signature);
    Exit e2 = new Exit(slot, validatorIndex.plus(randomUnsignedLong()), signature);

    assertNotEquals(e1, e2);
  }

  @Test
  void equalsReturnsFalseWhenSignaturesAreDifferent() {
    UnsignedLong slot = randomUnsignedLong();
    UnsignedLong validatorIndex = randomUnsignedLong();
    List<Bytes48> signature = Arrays.asList(Bytes48.random(), Bytes48.random());

    // Create copy of signature and reverse to ensure it is different.
    List<Bytes48> reverseSignature = new ArrayList<Bytes48>(signature);
    Collections.reverse(reverseSignature);

    Exit e1 = new Exit(slot, validatorIndex, signature);
    Exit e2 = new Exit(slot, validatorIndex, reverseSignature);

    assertNotEquals(e1, e2);
  }

  @Test
  void rountripSSZ() {
    Exit exit =
        new Exit(
            randomUnsignedLong(),
            randomUnsignedLong(),
            Arrays.asList(Bytes48.random(), Bytes48.random()));
    Bytes sszExitBytes = exit.toBytes();
    assertEquals(exit, Exit.fromBytes(sszExitBytes));
  }
}
