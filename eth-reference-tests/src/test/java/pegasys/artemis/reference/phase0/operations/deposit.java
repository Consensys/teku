package pegasys.artemis.reference.phase0.operations;

import com.google.errorprone.annotations.MustBeClosed;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pegasys.artemis.reference.TestSuite;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.process_deposit;

@ExtendWith(BouncyCastleExtension.class)
public class deposit extends TestSuite {

  @ParameterizedTest(name = "{index}. process Deposit deposit={0} pre={1} -> post{2} ")
  @MethodSource({"processInvalidWithdrawalCredentialTopUpSetup", "processNewDepositMaxSetup", "processNewDepositOverMaxSetup", "processNewDepositUnderMaxSetup", "processSuccessTopUpSetup"})
  void processDeposit(Deposit deposit, BeaconState pre,  BeaconState post) {
    process_deposit(pre, deposit);
    assertEquals(pre, post);
  }

  @MustBeClosed
  static Stream<Arguments> processInvalidWithdrawalCredentialTopUpSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/invalid_withdrawal_credentials_top_up");
    return operationDepositType3Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processNewDepositMaxSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/new_deposit_max");
    return operationDepositType3Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processNewDepositOverMaxSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/new_deposit_over_max");
    return operationDepositType3Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processNewDepositUnderMaxSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/new_deposit_under_max");
    return operationDepositType3Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processSuccessTopUpSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/success_top_up");
    return operationDepositType3Setup(path, configPath);
  }

  @ParameterizedTest(name = "{index}. process Deposit deposit={0} -> pre={1} ")
  @MethodSource({"processBadMerkleProofSetup", "processWrongDepositForDepositCountSetup"})
  void processDeposit(Deposit deposit, BeaconState pre) {
    process_deposit(pre, deposit);
    //TODO: process_deposit() should throw BlockProcessingException
    assertTrue(false);
  }

  @MustBeClosed
  static Stream<Arguments> processBadMerkleProofSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/bad_merkle_proof");
    return operationDepositType2Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processWrongDepositForDepositCountSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/wrong_deposit_for_deposit_count");
    return operationDepositType2Setup(path, configPath);
  }


  @ParameterizedTest(name = "{index}. process Deposit deposit={0} bls_setting={1} pre={2} -> post{3} ")
  @MethodSource({"processInvalidSigNewDepositSetup", "processInvalidSigTopUpSetup"})
  void processDeposit(Deposit deposit, Integer bls_setting, BeaconState pre, BeaconState post) {
    process_deposit(pre, deposit);
    assertEquals(pre, post);
    //TODO: process_deposit() should throw BlockProcessingException
    assertTrue(false);
  }

  @MustBeClosed
  static Stream<Arguments> processInvalidSigNewDepositSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/invalid_sig_new_deposit");
    return operationDepositType1Setup(path, configPath);
  }

  @MustBeClosed
  static Stream<Arguments> processInvalidSigTopUpSetup() throws Exception {
    Path configPath = Paths.get("mainnet", "phase0");
    Path path = Paths.get("/mainnet/phase0/operations/deposit/pyspec_tests/invalid_sig_top_up");
    return operationDepositType1Setup(path, configPath);
  }
}
