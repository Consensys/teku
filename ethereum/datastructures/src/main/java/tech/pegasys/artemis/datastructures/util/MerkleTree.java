
package tech.pegasys.artemis.datastructures.util;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;

import java.util.ArrayList;
import java.util.List;

public class MerkleTree<T> {
    private final List<List<Bytes32>> tree;
    private final List<Bytes32> zeroHashes;
    private final int height;
    private boolean dirty = true;

    public MerkleTree(int height) {
        assert(height > 1);
        this.height = height;
        tree = new ArrayList<List<Bytes32>>();
        for(int i = 0; i <= height; i++){
            tree.add(new ArrayList<Bytes32>());
        }
        zeroHashes = generateZeroHashes(height);
    }

    public void add(int index, Bytes32 leaf){
        dirty = true;
        tree.get(0).add(index, leaf);
    }

    private void calcBranches() {
        for(int i = 0; i < height; i++){
            List<Bytes32> parent = tree.get(i + 1);
            List<Bytes32> child = tree.get(i);
            for(int j = 0; j < child.size(); j+=2){
                Bytes32 leftNode = child.get(j);
                Bytes32 rightNode = (j+1 < child.size()) ? child.get(j+1) : zeroHashes.get(i);
                parent.add(j/2, Hash.sha2_256(Bytes.concatenate(leftNode, rightNode)));
            }
        }
        dirty = false;
    }

    public List<Bytes32> getProofTreeByValue(Bytes32 value){
        int index = tree.get(0).indexOf(value);
        return getProofTreeByIndex(index);
    }


    public List<Bytes32> getProofTreeByIndex(int index){
        if(dirty)calcBranches();
        List<Bytes32> proof = new ArrayList<Bytes32>();
        for(int i = 0; i < height; i++){
            index = index%2 == 1 ? index-1 : index+1;
            if(index < tree.get(i).size()) proof.add(tree.get(i).get(index));
            else proof.add(zeroHashes.get(i));
            index/=2;
        }
        return proof;
    }

    public static List<Bytes32> generateZeroHashes(int height){
        List<Bytes32> zeroHashes = new ArrayList<Bytes32>();
        for(int i=0; i < height; i++)zeroHashes.add(Bytes32.ZERO);
        for(int i=0; i < height-1; i++)zeroHashes.set(i+1, Hash.sha2_256(Bytes.concatenate(zeroHashes.get(i), zeroHashes.get(i))));
        return zeroHashes;
    }

    public Bytes32 getRoot(){
        if(dirty)calcBranches();
        return tree.get(height).get(0);
    }
}