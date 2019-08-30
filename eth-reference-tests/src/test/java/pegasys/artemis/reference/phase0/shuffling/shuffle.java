package pegasys.artemis.reference.phase0.shuffling;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.artemis.datastructures.util.CrosslinkCommitteeUtil.compute_shuffled_index;

@ExtendWith(BouncyCastleExtension.class)
public class shuffle extends TestSuite {

  @ParameterizedTest(name = "{index} root of Merkleizable")
  @MethodSource({"shufflingGenericShuffleSetup"})
  void sanityProcessSlot(
          Bytes32 seed, Integer count, List<Integer> mapping) {
    IntStream.range(0, count).forEach(i -> {
      assertEquals(compute_shuffled_index(i, count, seed), mapping.get(i));
    });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @MustBeClosed
  static Stream<Arguments> shufflingGenericShuffleSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/shuffling/core/shuffle");
    return shufflingShuffleSetup(path, configPath);
  }
}
