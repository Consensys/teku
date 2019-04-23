package tech.pegasys.artemis.validator.client;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.gas.DefaultGasProvider;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.mikuli.BLS12381;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.PublicKey;

import java.math.BigInteger;

public class ValidatorClientUtil {

    public static Bytes generateDepositData(KeyPair blsKeys, Bytes32 withdrawal_credentials, UnsignedLong amount){
        Bytes deposit_data =
                Bytes.wrap(
                        Bytes.ofUnsignedLong(amount.longValue()),
                        withdrawal_credentials,
                        getPublicKeyFromKeyPair(blsKeys).toBytesCompressed()
                );
        return Bytes.wrap(generateProofOfPossession(blsKeys, deposit_data), deposit_data).reverse();
    }

    public static Bytes generateProofOfPossession(KeyPair blsKeys, Bytes deposit_data){
        return BLS12381
                .sign(blsKeys, deposit_data, Constants.DOMAIN_DEPOSIT)
                .signature()
                .toBytesCompressed();
    }

    public static PublicKey getPublicKeyFromKeyPair(KeyPair blsKeys){
        return BLSPublicKey.fromBytesCompressed(blsKeys.publicKey().toBytesCompressed()).getPublicKey();
    }

    public static void registerValidatorEth1(
            Validator validator,
            UnsignedLong amount,
            String address,
            Web3j web3j,
            DefaultGasProvider gasProvider)
            throws Exception {
        Credentials credentials =
                Credentials.create(validator.getSecpKeys().secretKey().bytes().toHexString());
        DepositContract contract = null;
        Bytes deposit_data = generateDepositData(validator.getBlsKeys(), validator.getWithdrawal_credentials(), amount);
        contract = DepositContract.load(address, web3j, credentials, gasProvider);
        contract
                .deposit(deposit_data.toArray(), new BigInteger(amount.toString() + "000000000"))
                .send();
    }
}
