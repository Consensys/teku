package tech.pegasys.artemis.pow.tech.pegasys.artemis.datastructures;

import com.google.common.primitives.UnsignedLong;

public class DepositData {
    //Amount in Gwei
    UnsignedLong amount;
    //Timestamp from deposit contract
    UnsignedLong timestamp;
    //Deposit input
    DepositInput deposit_input;

    public DepositData(UnsignedLong amount, UnsignedLong timestamp, DepositInput deposit_input) {
        this.amount = amount;
        this.timestamp = timestamp;
        this.deposit_input = deposit_input;
    }

    public UnsignedLong getAmount() {
        return amount;
    }

    public void setAmount(UnsignedLong amount) {
        this.amount = amount;
    }

    public UnsignedLong getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(UnsignedLong timestamp) {
        this.timestamp = timestamp;
    }

    public DepositInput getDeposit_input() {
        return deposit_input;
    }

    public void setDeposit_input(DepositInput deposit_input) {
        this.deposit_input = deposit_input;
    }
}
