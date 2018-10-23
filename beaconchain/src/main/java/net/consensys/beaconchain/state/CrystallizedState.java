package net.consensys.beaconchain.state;

import java.nio.ByteBuffer;
import java.util.Arrays;
import net.consensys.beaconchain.datastructures.CrosslinkRecord;
import net.consensys.beaconchain.datastructures.ListOfValidators;
import net.consensys.beaconchain.datastructures.ShardAndCommittee;
import net.consensys.beaconchain.datastructures.ValidatorRecord;
import net.consensys.beaconchain.ethereum.core.Hash;

import org.web3j.abi.datatypes.generated.Bytes3;
import org.web3j.abi.datatypes.generated.Int32;
import org.web3j.abi.datatypes.generated.Int64;

public class CrystallizedState {

  private CrosslinkRecord[] crosslink_records;
  private Hash dynasty_seed;
  private Hash validator_set_delta_hash_chain;
  private Int32[] deposits_penalized_in_period;
  private Int64 current_dynasty;
  private Int64 dynasty_start;
  private Int64 justified_streak;
  private Int64 last_finalized_slot;
  private Int64 last_justified_slot;
  private Int64 last_state_recalculation;
  private ListOfValidators validators;
  private ShardAndCommittee[][] shard_and_committee_for_slots;

  public CrystallizedState() {}



  private static class CrystallizedStateOperators {


    /**
     * Converts int to Bytes3.
     *
     * @param seed  converted
     * @return      converted Bytes3
     */
    private static Bytes3 toBytes3(int seed) {
      byte[] bytes = new byte[3];
      bytes[0] = (byte) (seed >> 16);
      bytes[1] = (byte) (seed >> 8);
      bytes[2] = (byte) seed;
      return new Bytes3(bytes);
    }

    /**
     * Converts byte[] to int.
     *
     * @param src   byte[]
     * @param pos   Index in Byte[] array
     * @return      converted int
     */
    private static int fromBytes3(byte[] src, int pos) {
      return ((src[pos] & 0xF) << 16) |
          ((src[pos+1] & 0xFF) << 8) |
          (src[pos+2] & 0xFF);
    }

    /**
     * Shuffles an array.
     *
     * @param arr   The array.
     * @param seed  Initial seed value used for randomization.
     * @return
     */
    private static ValidatorRecord[] shuffle(ValidatorRecord[] arr, int seed) {
      int rand_max = (int) Math.pow(2, 24);
      assert arr.length <= rand_max;

      ValidatorRecord[] shuffled_arr = arr.clone();
      ValidatorRecord[] tmp = new ValidatorRecord[arr.length];
      Bytes3 source = new Bytes3(new byte[0]);
      int i = 0;

      while (i < arr.length) {

        if (i < 1) {
          source = toBytes3(seed);
        } else {
          source = new Bytes3(source.getValue());
        }

        for (int pos = 0; pos < 30; pos += 3) {

          byte[] src = source.getValue();
          int m = fromBytes3(src, pos);

          int remaining = arr.length - i;
          if (remaining == 0)
            break;
          rand_max = rand_max - rand_max % remaining;
          if (0 < rand_max) {
            int replacement_pos = (0 % remaining) + i;
            tmp[i] = shuffled_arr[i];
            shuffled_arr[i] = shuffled_arr[replacement_pos];
            shuffled_arr[replacement_pos] = tmp[i];
            i += 1;
          }
        }

      }

      return shuffled_arr;
    }


    /**
     * Splits an array into N pieces.
     *
     * @param arr   The original list of validators.
     * @param N     The number of pieces to split the array into.
     * @return      The list of validators split into N pieces.
     */
    private static ValidatorRecord[][] split(ValidatorRecord[] arr, int N) {
      // TODO: Set more accurate size of double array.
      ValidatorRecord[][] split_arr = new ValidatorRecord[N][arr.length];
      for (int i = 0; i < N; i++) {
        int startIndex = arr.length * i/N;
        int endIndex = arr.length * (i + 1)/N;
        ValidatorRecord[] new_split = Arrays.copyOfRange(arr, startIndex, endIndex);
        split_arr[i] = new_split;
      }
      return split_arr;
    }
  }
}
