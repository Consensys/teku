package net.consensys.beaconchain.state;

//import net.consensys.beaconchain.datastructures.ValidatorRecord;
import net.consensys.beaconchain.state.CrystallizedState.CrystallizedStateOperators;
import org.junit.Test;


import org.web3j.abi.datatypes.generated.Bytes3;

import static org.junit.Assert.assertEquals;

public class CrystallizedStateTest {


  /** Validates int to Bytes3 conversion. */
  @Test
  public void toBytes3Test() {

    int seed = 212420;
    byte[] bytes = {(byte) 3, (byte) 829, (byte) 212420};

    Bytes3 expected = new Bytes3(bytes);
    Bytes3 actual = CrystallizedStateOperators.toBytes3(seed);

    assertEquals(expected, actual);
  }


  /** Validate Bytes3 to int conversion. */
  @Test
  public void fromBytes3Test() {

    byte[] src = new byte[3];
    int pos = 0;

    int expected = 30;
    int actual = CrystallizedStateOperators.fromBytes3(src, pos);

    assertEquals(expected, actual);
  }


//  /** Validate shuffle. */
//  @Test
//  public void shuffleTest() {
//
//    ValidatorRecord[] expected = new ValidatorRecord[];
//    ValidatorRecord[] actual = CrystallizedStateOperators.shuffle();
//
//    assertEquals(expected, actual);
//  }
//
//
//  /** Validate split. */
//  @Test
//  public void splitTest() {
//
//    ValidatorRecord[][] expected = new ValidatorRecord[][];
//    ValidatorRecord[][] actual = CrystallizedStateOperators.split();
//
//    assertEquals(expected, actual);
//  }


}
