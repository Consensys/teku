package net.consensys.beaconchain.state;

import java.util.Arrays;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import net.consensys.beaconchain.datastructures.CrosslinkRecord;
import net.consensys.beaconchain.datastructures.ListOfValidators;
import net.consensys.beaconchain.datastructures.ShardAndCommittee;
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


  static class CrystallizedStateOperators {


    /**
     * Converts int to Bytes3.
     *
     * @param seed  converted
     * @return      converted Bytes3
     */
    @VisibleForTesting
    static Bytes3 toBytes3(int seed) {
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
    @VisibleForTesting
    static int fromBytes3(Hash src, int pos) {
      return ((src.extractArray()[pos] & 0xF) << 16) |
          ((src.extractArray()[pos + 1] & 0xFF) << 8) |
          (src.extractArray()[pos + 2] & 0xFF);
    }

    /**
     * Returns the shuffled ``values`` with seed as entropy.
     *
     * @param values    The array.
     * @param seed      Initial seed value used for randomization.
     * @return          The shuffled array.
     */
    @VisibleForTesting
    static List<T> shuffle(List values, Hash seed) {

      int values_count = values.length;

      // Entropy is consumed from the seed in 3-byte (24 bit) chunks.
      int rand_bytes = 3;
      // The highest possible result of the RNG.
      int rand_max = (int) Math.pow(2, (rand_bytes * 8) - 1);

      // The range of the RNG places an upper-bound on the size of the list that
      // may be shuffled. It is a logic error to supply an oversized list.
      assert values_count < rand_max;

      List[] output = values.clone();
      Hash source = seed;
      int index = 0;

      while (index < values_count - 1) {
        // Re-hash the `source` to obtain a new pattern of bytes.
        source = Hash.hash(source);

        // List to hold values for swap below.
        List tmp;

        // Iterate through the `source` bytes in 3-byte chunks
        for (int position = 0; position < (32 - (32 % rand_bytes)); position += rand_bytes) {
          // Determine the number of indices remaining in `values` and exit
          // once the last index is reached.
          int remaining = values_count - index;
          if (remaining == 1) break;

          // Read 3-bytes of `source` as a 24-bit big-endian integer.
          int sample_from_source = fromBytes3(source, position);


          // Sample values greater than or equal to `sample_max` will cause
          // modulo bias when mapped into the `remaining` range.
          int sample_max = rand_max - rand_max % remaining;

          // Perform a swap if the consumed entropy will not cause modulo bias.
          if (sample_from_source < sample_max) {
            // Select a replacement index for the current index
            int replacement_position = (sample_from_source % remaining) + index;
            // Swap the current index with the replacement index.
            tmp = output[index];
            output[index] = output[replacement_position];
            output[replacement_position] = tmp;
            index += 1;
          }
        }
      }

      return output;
    }


    /**
     * Returns the split ``seq`` in ``split_count`` pieces in protocol.
     *
     * @param seq             The original list of validators.
     * @param split_count     The number of pieces to split the array into.
     * @return                The list of validators split into N pieces.
     */
    static List[][] split(List[] seq, int split_count) {

      int list_length = seq.length;

      List[][] split_arr = new List[split_count][seq.length];

      for (int i = 0; i < split_count; i++) {
        int startIndex = list_length * i / split_count;
        int endIndex = list_length * (i + 1) / split_count;
        List[] new_split = Arrays.copyOfRange(seq, startIndex, endIndex);
        split_arr[i] = new_split;
      }

      return split_arr;

    }

    /**
     * A helper method for readability.
     */
    static int clamp(int minval, int maxval, int x) {
      if (x <= minval) return minval;
      if (x >= maxval) return maxval;
      return x;
    }


  }
}
