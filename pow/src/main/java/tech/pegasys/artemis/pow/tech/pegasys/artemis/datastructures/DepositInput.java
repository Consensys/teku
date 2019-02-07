package tech.pegasys.artemis.pow.tech.pegasys.artemis.datastructures;

import net.consensys.cava.bytes.Bytes32;
import net.consensys.cava.bytes.Bytes48;

import java.util.ArrayList;

public class DepositInput {
    //BLS pubkey
    Bytes48 pubkey;
    //Withdrawal credentials
    Bytes32 withdrawal_credentials;
    //A BLS signature of this `DepositInput`
    ArrayList<Bytes48> proof_of_possession;

    public DepositInput(Bytes48 pubkey, Bytes32 withdrawal_credentials, ArrayList<Bytes48> proof_of_possession) {
        this.pubkey = pubkey;
        this.withdrawal_credentials = withdrawal_credentials;
        this.proof_of_possession = proof_of_possession;
    }

    public Bytes48 getPubkey() {
        return pubkey;
    }

    public void setPubkey(Bytes48 pubkey) {
        this.pubkey = pubkey;
    }

    public Bytes32 getWithdrawal_credentials() {
        return withdrawal_credentials;
    }

    public void setWithdrawal_credentials(Bytes32 withdrawal_credentials) {
        this.withdrawal_credentials = withdrawal_credentials;
    }

    public ArrayList<Bytes48> getProof_of_possession() {
        return proof_of_possession;
    }

    public void setProof_of_possession(ArrayList<Bytes48> proof_of_possession) {
        this.proof_of_possession = proof_of_possession;
    }
}
