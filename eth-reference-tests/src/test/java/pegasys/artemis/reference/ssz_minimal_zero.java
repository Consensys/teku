package pegasys.artemis.reference;

import kotlin.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.artemis.datastructures.operations.Attestation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@ExtendWith(BouncyCastleExtension.class)
class ssz_minimal_zero extends TestSuite {

  private static String testFile = "/eth2.0-spec-tests/tests/ssz_static/core/ssz_minimal_zero.yaml";

  @ParameterizedTest(name = "{index}. SSZ serialized, root, signing_root of Attestation")
  @MethodSource("readMessageSSZAttestation")
  void sszAttestationCheckSerializationRootAndSigningRoot(Attestation attestation, Bytes serialized, Bytes32 root, Bytes signing_root) {
  }

  static Stream<Arguments> readMessageSSZAttestation() throws IOException {
    List<Pair<Class, List<String>>> arguments = new ArrayList<Pair<Class, List<String>>>();
    arguments.add(getParams(Attestation.class,  Arrays.asList("test_cases", "0", "Attestation", "value")));
    arguments.add(getParams(Bytes.class,  Arrays.asList("test_cases", "0", "Attestation", "serialized")));
    arguments.add(getParams(Bytes32.class,  Arrays.asList("test_cases", "0", "Attestation", "root")));
    arguments.add(getParams(Bytes.class,  Arrays.asList("test_cases", "0", "Attestation", "signing_root")));

    return findTests(testFile, arguments);
  }
}
