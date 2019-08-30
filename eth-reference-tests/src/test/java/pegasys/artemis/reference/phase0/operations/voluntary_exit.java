package pegasys.artemis.reference.phase0.operations;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.util.BlockProcessingException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.artemis.statetransition.util.BlockProcessorUtil.process_voluntary_exits;

@ExtendWith(BouncyCastleExtension.class)
public class voluntary_exit extends TestSuite {
  @ParameterizedTest(name = "{index}. process VoluntaryExit voluntaryExit={0} bls_setting={1} -> pre={2} ")
  @MethodSource({"processVoluntaryExitInvalidSignatureSetup"})
  void processProposerSlashing(VoluntaryExit voluntaryExit, Integer bls_setting, BeaconState pre) {
    List<VoluntaryExit> voluntaryExits = new ArrayList<VoluntaryExit>();
    voluntaryExits.add(voluntaryExit);
    assertThrows(BlockProcessingException.class, () -> {
      process_voluntary_exits(pre, voluntaryExits);
    });
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitInvalidSignatureSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/invalid_signature");
    return operationVoluntaryExitType1Setup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process VoluntaryExit voluntaryExit={0} -> pre={1} ")
  @MethodSource({"processVoluntaryExitValidatorAlreadyExitedSetup", "processVoluntaryExitValidatorExitInFutureSetup", "processVoluntaryExitValidatorInvalidValidatorIndexSetup", "processVoluntaryExitValidatorNotActiveSetup", "processVoluntaryExitValidatorNotActiveLongEnoughSetup"})
  void processProposerSlashing(VoluntaryExit voluntaryExit, BeaconState pre) {
    List<VoluntaryExit> voluntaryExits = new ArrayList<VoluntaryExit>();
    voluntaryExits.add(voluntaryExit);
    assertThrows(BlockProcessingException.class, () -> {
      process_voluntary_exits(pre, voluntaryExits);
    });
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitValidatorAlreadyExitedSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/validator_already_exited");
    return operationVoluntaryExitType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitValidatorExitInFutureSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/validator_exit_in_future");
    return operationVoluntaryExitType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitValidatorInvalidValidatorIndexSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/validator_invalid_validator_index");
    return operationVoluntaryExitType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitValidatorNotActiveSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/validator_not_active");
    return operationVoluntaryExitType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitValidatorNotActiveLongEnoughSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/validator_not_active_long_enough");
    return operationVoluntaryExitType2Setup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process VoluntaryExit voluntaryExit={0} pre={1} -> post={2} ")
  @MethodSource({"processVoluntaryExitSuccesSetup", "processVoluntarySuccessExitQueueSetup"})
  void processProposerSlashing(VoluntaryExit voluntaryExit, BeaconState pre, BeaconState post) {
    List<VoluntaryExit> voluntaryExits = new ArrayList<VoluntaryExit>();
    voluntaryExits.add(voluntaryExit);
    assertDoesNotThrow(() -> {
      process_voluntary_exits(pre, voluntaryExits);
    });
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntaryExitSuccesSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/success");
    return operationVoluntaryExitType3Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processVoluntarySuccessExitQueueSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/voluntary_exit/pyspec_tests/success_exit_queue");
    return operationVoluntaryExitType3Setup(path, configPath);
  }
}
