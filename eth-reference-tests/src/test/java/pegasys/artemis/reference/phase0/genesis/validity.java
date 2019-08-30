package pegasys.artemis.reference.phase0.genesis;


import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@ExtendWith(BouncyCastleExtension.class)
public class validity extends TestSuite {

  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({"genesisGenericValiditySetup"})
  void genesisValidity(
          BeaconState genesis, Boolean is_valid) {
    //TODO
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> genesisGenericValiditySetup() throws Exception {
    Path configPath = Paths.get("minimal", "phase0");
    Path path = Paths.get("/minimal/phase0/genesis/validity/pyspec_tests");
    return genesisValiditySetup(path, configPath);
  }
}
