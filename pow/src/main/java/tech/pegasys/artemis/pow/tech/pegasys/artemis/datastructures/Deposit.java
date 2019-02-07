package tech.pegasys.artemis.pow.tech.pegasys.artemis.datastructures;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;

import java.util.ArrayList;

public class Deposit {
    //Branch in the deposit tree
    ArrayList<Bytes32> branch;
    //Index in the deposit tree
    UnsignedLong index;
    //Data
    DepositData deposit_data;

    public Deposit(ArrayList<Bytes32> branch, UnsignedLong index, DepositData deposit_data) {
        this.branch = branch;
        this.index = index;
        this.deposit_data = deposit_data;
    }

    public ArrayList<Bytes32> getBranch() {
        return branch;
    }

    public void setBranch(ArrayList<Bytes32> branch) {
        this.branch = branch;
    }

    public UnsignedLong getIndex() {
        return index;
    }

    public void setIndex(UnsignedLong index) {
        this.index = index;
    }

    public DepositData getDeposit_data() {
        return deposit_data;
    }

    public void setDeposit_data(DepositData deposit_data) {
        this.deposit_data = deposit_data;
    }
}
