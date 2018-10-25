package net.consensys.beaconchain;

public final class BeaconChain {

  // Genesis time will be set programmatically when the BeaconChain starts
  final int GENESIS_TIME;

  // The constants below are correct as of spec dated 2018/10/25
  static final int SHARD_COUNT                       = (int)Math.pow(2, 10); // 1,024 Shards
  static final int DEPOSIT_SIZE                      = (int)Math.pow(2, 5);  // 32 Eth
  static final int MIN_ONLINE_DEPOSIT_SIZE           = (int)Math.pow(2, 4);  // 16 Eth
  static final int GWEI_PER_ETH                      = (int)Math.pow(10, 9); // 1,000,000,000 Wei
  static final int MIN_COMMITTEE_SIZE                = (int)Math.pow(2, 7);  // 128 Validators
  static final int SLOT_DURATION                     = (int)Math.pow(2, 4);  // 16 seconds
  static final int CYCLE_LENGTH                      = (int)Math.pow(2, 6);  // 64 Slots
  static final int MIN_VALIDATOR_SET_CHANGE_INTERVAL = (int)Math.pow(2, 8);  // 256 Slots
  static final int RANDAO_SLOTS_PER_LAYER            = (int)Math.pow(2, 12); // 4,096 Slots
  static final int SQRT_E_DROP_TIME                  = (int)Math.pow(2, 16); // 65,536 Slots
  static final int WITHDRAWAL_PERIOD                 = (int)Math.pow(2, 19); // 524,288 Slots
  static final int BASE_REWARD_QUOTIENT              = (int)Math.pow(2, 15); // 32,768
  static final int MAX_VALIDATOR_CHURN_QUOTIENT      = (int)Math.pow(2, 5);  // 32
  static final String LOGOUT_MESSAGE                 = "LOGOUT";
  static final int INITIAL_FORK_VERSION              = 0;

  // Constructor
  public BeaconChain(int genesisTime) {
    GENESIS_TIME = genesisTime;
  }

  public static String getConstantsAsString() {
    return "SHARD_COUNT: " + SHARD_COUNT
            + "\nDEPOSIT_SIZE: " + DEPOSIT_SIZE
            + "\nMIN_ONLINE_DEPOSIT_SIZE: " + MIN_ONLINE_DEPOSIT_SIZE
            + "\nGWEI_PER_ETH: " + GWEI_PER_ETH
            + "\nMIN_COMMITTEE_SIZE: " + MIN_COMMITTEE_SIZE
            + "\nSLOT_DURATION: " + SLOT_DURATION
            + "\nCYCLE_LENGTH: " + CYCLE_LENGTH
            + "\nMIN_VALIDATOR_SET_CHANGE_INTERVAL: " + MIN_VALIDATOR_SET_CHANGE_INTERVAL
            + "\nRANDAO_SLOTS_PER_LAYER: " + RANDAO_SLOTS_PER_LAYER
            + "\nSQRT_E_DROP_TIME: " + SQRT_E_DROP_TIME
            + "\nWITHDRAWAL_PERIOD: " + WITHDRAWAL_PERIOD
            + "\nBASE_REWARD_QUOTIENT: " + BASE_REWARD_QUOTIENT
            + "\nMAX_VALIDATOR_CHURN_QUOTIENT: " + MAX_VALIDATOR_CHURN_QUOTIENT
            + "\nLOGOUT_MESSAGE: " + LOGOUT_MESSAGE
            + "\nINITIAL_FORK_VERSION: " + INITIAL_FORK_VERSION
            + "\n";
  }

}
