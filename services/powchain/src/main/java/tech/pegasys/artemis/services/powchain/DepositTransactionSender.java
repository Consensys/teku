package tech.pegasys.artemis.services.powchain;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_DEPOSIT;

import com.google.common.primitives.UnsignedLong;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class DepositTransactionSender {

  private final MockStartDepositGenerator depositGenerator = new MockStartDepositGenerator();
  private final DepositContract depositContract;

  public DepositTransactionSender(final DepositContract depositContract) {
    this.depositContract = depositContract;
  }

  public CompletableFuture<TransactionReceipt> sendDepositTransaction(
      BLSKeyPair validatorKeyPair, BLSKeyPair withdrawalKeyPair, final UnsignedLong amountInGwei)
      throws Exception {
    final Bytes32 withdrawalCredentials =
        depositGenerator.createWithdrawalCredentials(withdrawalKeyPair);
    final DepositData depositData =
        new DepositData(validatorKeyPair.getPublicKey(), withdrawalCredentials, amountInGwei, null);

    depositData.setSignature(
        BLSSignature.sign(
            validatorKeyPair,
            depositData.signing_root("signature"),
            compute_domain(DOMAIN_DEPOSIT)));

    return sendDepositTransaction(depositData);
  }

  private CompletableFuture<TransactionReceipt> sendDepositTransaction(final DepositData depositData) throws Exception {
    return depositContract.deposit(
        depositData.getPubkey().toBytesCompressed().toArray(),
        depositData.getWithdrawal_credentials().toArray(),
        depositData.getSignature().getSignature().toBytesCompressed().toArray(),
        depositData.hash_tree_root().toArray(),
        new BigInteger(depositData.getAmount() + "000000000")).sendAsync();
  }
}
