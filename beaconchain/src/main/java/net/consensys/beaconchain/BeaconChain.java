package net.consensys.beaconchain;

import java.lang.Math;
import java.time.Instant;
import org.web3j.abi.datatypes.generated.Int64;

public final class BeaconChain {

  private final long GENESIS_TIME;
  private static final double BASE_REWARD_QUOTIENT = Math.pow(2, 15);
  private static final Int64 DEPOSIT_SIZE = 32;
  private static final Int64 MAX_VALIDATOR_CHANGE_QUOTIENT = 32;
  private static final Int64 MAX_VALIDATOR_COUNT = 4194304;
  private static final Int64 MIN_COMMITTEE_SIZE = 128;
  private static final Int64 MIN_DYNASTY_LENGTH = 256;
  private static final Int64 SHARD_COUNT = 1024;
  private static final Int64 SLOT_DURATION = 8;
  private static final Int64 CYCLE_LENGTH = 64 * SLOT_DURATION; // 64 slots * 8s/slot
  private static final double SQRT_E_DROP_TIME = Math.pow(2, 20);
  private static final double WITHDRAWAL_PERIOD = Math.pow(2, 19);

  public BeaconChain() {
    GENESIS_TIME = Instant.now().getEpochSecond();
  }

}
