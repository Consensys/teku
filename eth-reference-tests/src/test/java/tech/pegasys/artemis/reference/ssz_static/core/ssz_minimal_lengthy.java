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

package tech.pegasys.artemis.reference.ssz_static.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.errorprone.annotations.MustBeClosed;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.reference.TestSuite;

@ExtendWith(BouncyCastleExtension.class)
class ssz_minimal_lengthy extends TestSuite {
  private static String testFile = "**/ssz_minimal_lengthy.yaml";

  @ParameterizedTest(name = "{index}. SSZ serialized, root, signing_root of Attestation")
  @MethodSource("readMessageSSZAttestation")
  void sszAttestationCheckSerializationRootAndSigningRoot(
      Attestation attestation, Bytes serialized, Bytes32 root, Bytes signing_root) {

    /*
    Check after serialization
    assertEquals(
        serialized,
        attestation.toBytes(),
        attestation.getClass().getName() + " failed the serialiaztion test");
        */
    assertEquals(
        root,
        attestation.hash_tree_root(),
        attestation.getClass().getName() + " failed the root test");
    assertEquals(
        root,
        attestation.signing_root("signature"),
        attestation.getClass().getName() + " failed the signing_root test");
  }

  @SuppressWarnings({"rawtypes"})
  @MustBeClosed
  static Stream<Arguments> readMessageSSZAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Attestation.class, Arrays.asList("Attestation", "value")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Attestation", "serialized")));
    arguments.add(getParams(Bytes32.class, Arrays.asList("Attestation", "root")));
    arguments.add(getParams(Bytes.class, Arrays.asList("Attestation", "signing_root")));

    return findTests(testFile, arguments);
  }
}
