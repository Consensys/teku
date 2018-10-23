package net.consensys.beaconchain;

import java.time.Instant;

import org.web3j.abi.datatypes.generated.Int64;

public final class BeaconChain {

  private final long GENESIS_TIME;
  private static final double BASE_REWARD_QUOTIENT = Math.pow(2, 15);
  private static final Int64 DEPOSIT_SIZE = new Int64(32L);
  private static final Int64 MAX_VALIDATOR_CHANGE_QUOTIENT = new Int64(32L);
  private static final Int64 MAX_VALIDATOR_COUNT = new Int64(4194304L);
  private static final Int64 MIN_COMMITTEE_SIZE = new Int64(128L);
  private static final Int64 MIN_DYNASTY_LENGTH = new Int64(256L);
  private static final Int64 SHARD_COUNT = new Int64(1024L);
  private static final Int64 SLOT_DURATION = new Int64(8L);
  private static final Int64 CYCLE_LENGTH = new Int64(64L * 8L); // 64 slots * 8s/slot
  private static final double SQRT_E_DROP_TIME = Math.pow(2, 20);
  private static final double WITHDRAWAL_PERIOD = Math.pow(2, 19);


  public BeaconChain() {
    GENESIS_TIME = Instant.now().getEpochSecond();

  }

}
