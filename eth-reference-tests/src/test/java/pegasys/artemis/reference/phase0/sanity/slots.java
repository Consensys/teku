package pegasys.artemis.reference.phase0.sanity;

import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.statetransition.StateTransition;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BouncyCastleExtension.class)
public class slots extends TestSuite {

  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({"sanityGenericSlotSetup"})
  void sanityProcessSlot(
          BeaconStateWithCache pre, BeaconStateWithCache post, UnsignedLong slot) {
    assertDoesNotThrow(() -> StateTransition.process_slots(pre, slot, false));
    assertEquals(pre, post);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> sanityGenericSlotSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/sanity/slots/pyspec_tests");
    return sanitySlotSetup(path, configPath);
  }
}
