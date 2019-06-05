package tech.pegasys.artemis.datastructures.util;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.datastructures.operations.Deposit;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static tech.pegasys.artemis.datastructures.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;

public class DepositUtil {

    public static List<Deposit> generateBranchProofs(List<Deposit> deposits){
        deposits = sortDepositsByIndexAscending(deposits);
        MerkleTree<Deposit> merkleTree = new MerkleTree<Deposit>(DEPOSIT_CONTRACT_TREE_DEPTH);

        for(int i = 0; i < deposits.size(); i++)merkleTree.add(deposits.get(i).getIndex().intValue(), Hash.sha2_256(deposits.get(i).getDeposit_data().serialize()));
        for(int i = 0; i < deposits.size(); i++)deposits.get(i).setProof(merkleTree.getProofTreeByIndex(i));
        return deposits;
    }

    public static boolean validateDeposits(List<Deposit> deposits, Bytes32 root, int height){
        for(int i = 0; i < deposits.size(); i++){
            if(BeaconStateUtil.verify_merkle_branch(Hash.sha2_256(deposits.get(i).getDeposit_data().serialize()), deposits.get(i).getProof(), height, i, root));
            else return false;
        }
        return true;
    }

    public static List<Bytes32> insertBranch(int branchIndex, Bytes32 branchValue, List<Bytes32> branch, int height){
        Bytes32 value = branchValue;
        for(int j =0; j < height; j++){
            if(j < branchIndex){
                value = Hash.sha2_256(Bytes.concatenate(branch.get(j), value));
            }
            else break;
        }
        branch.set(branchIndex, value);
        return branch;
    }

    public static Bytes32 calculateRoot(List<Bytes32> branch, List<Bytes32> zerohashes, int deposit_count, int height){
        Bytes32 root = Bytes32.ZERO;
        int size = deposit_count;
        for(int h = 0; h < height; h++){
            if(size % 2 != 0){
                root = Hash.sha2_256(Bytes.concatenate(branch.get(h), root));
            }
            else{
                root = Hash.sha2_256(Bytes.concatenate(root, zerohashes.get(h)));
            }
            size/=2;
        }
        return root;
    }

    public static int calculateBranchIndex(int index, int height){
        int branchIndex = 0;
        int power_of_two = 2;
        for(int x = 0; x < height; x++){
            if((index+1)%power_of_two != 0) break;
            branchIndex++;
            power_of_two *= 2;
        }
        return branchIndex;
    }

    public static Bytes32 calculatePubKeyRoot(Deposit deposit){
        return Hash.sha2_256(Bytes.concatenate(deposit.getDeposit_data().getDeposit_input().getPubkey().getPublicKey().toBytesCompressed(), Bytes.wrap(new byte[16])));
    }

    public static Bytes32 calculateSignatureRoot(Deposit deposit){
        Bytes32 signature_root_start = Hash.sha2_256(Bytes.wrap(Arrays.copyOfRange(deposit.getDeposit_data().getDeposit_input().getProof_of_possession().getSignature().toBytesCompressed().toArray(), 0, 64)));
        Bytes signature_root_end = Hash.sha2_256(Bytes.concatenate(Bytes.wrap(Arrays.copyOfRange(deposit.getDeposit_data().getDeposit_input().getProof_of_possession().getSignature().toBytesCompressed().toArray(), 64, 96)), Bytes32.ZERO));
        return Hash.sha2_256(Bytes.concatenate(signature_root_start, signature_root_end));
    }

    public static Bytes32 calculateBranchValue(Bytes32 pubkey_root, Bytes32 signature_root, Deposit deposit){
        Bytes32 value_start = Hash.sha2_256(Bytes.concatenate(pubkey_root, deposit.getDeposit_data().getDeposit_input().getWithdrawal_credentials()));
        Bytes32 value_end = Hash.sha2_256(Bytes.concatenate(Bytes.ofUnsignedLong(deposit.getDeposit_data().getAmount().longValue()), Bytes.wrap(new byte[24]), signature_root));

        return Hash.sha2_256(Bytes.concatenate(value_start, value_end));
    }

    public static List<Deposit> sortDepositsByIndexAscending(List<Deposit> deposits){
        deposits.sort(new Comparator<Deposit>() {
            @Override
            public int compare(Deposit o1, Deposit o2) {
                return o1.getIndex().compareTo(o2.getIndex());
            }
        });
        return deposits;
    }
}