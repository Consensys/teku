package pegasys.artemis.reference.phase0.operations;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_attestations;

@ExtendWith(BouncyCastleExtension.class)
public class attestation extends TestSuite {

  @ParameterizedTest(name = "{index}. process Attestation attestation={0} -> pre={1}")
  @MethodSource("processAttestationSetup")
  void processAttestation(Attestation attestation, BeaconState pre) {
    List<Attestation> attestations = new ArrayList<Attestation>();
    attestations.add(attestation);
    assertThrows(BlockProcessingException.class, () -> {
      process_attestations(pre, attestations);
    });
  }

  @MustBeClosed
  static Stream<Arguments> processAttestationSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("mainnet", "phase0", "operations", "attestation", "pyspec_tests");
    return sszStaticAttestationSetup(path, configPath);
  }
}
