package tech.pegasys.artemis.util.hashtree;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class MerkleUtil {

  public static int get_next_power_of_two(int x){
    //Get next power of 2 >= the input.
    return (x <= 2) ? x : 2 * get_next_power_of_two((x + 1) / 2);
  }

  public static int get_previous_power_of_two(int x){
    //Get the previous power of 2 >= the input
    return (x <= 2) ? x : 2 * get_previous_power_of_two(x / 2);
  }


  public static SSZVector<Bytes32> merkle_tree(SSZVector<Bytes32> leaves){
    int padded_length = get_next_power_of_two(leaves.size());
    List<Bytes32> placeholder = new ArrayList<Bytes32>(padded_length);
    placeholder.addAll(leaves);
    placeholder.addAll(new ArrayList<Bytes32>(padded_length - leaves.size()));
    SSZVector<Bytes32> o = new SSZVector<Bytes32>(placeholder, Bytes32.class);

    int start = 0;
    IntStream.range(start, padded_length)
            .map(i -> start + (padded_length - 1 - i))
            .forEach(i -> {
              o.set(i, Hash.sha2_256(Bytes.concatenate(o.get(i * 2), o.get(i * 2 + 1))));
            });
    return o;
  }

}
