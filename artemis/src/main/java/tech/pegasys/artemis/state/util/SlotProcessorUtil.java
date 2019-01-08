package tech.pegasys.artemis.state.util;

import tech.pegasys.artemis.ethereum.core.Hash;
import tech.pegasys.artemis.util.bytes.Bytes32;

import java.util.ArrayList;

public class SlotProcessorUtil {
    public static Hash merkle_root(ArrayList<Hash> latest_block_roots){
        //todo
        return Hash.wrap(Bytes32.FALSE);
    }
}
