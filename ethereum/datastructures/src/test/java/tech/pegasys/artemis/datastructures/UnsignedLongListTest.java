package tech.pegasys.artemis.datastructures;

import com.google.common.primitives.UnsignedLong;
import net.consensys.cava.bytes.Bytes32;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnsignedLongListTest {

    UnsignedLongList setup(){
        return new UnsignedLongList();
    }
    @Test
    void size() {
        UnsignedLongList<Bytes32> list = setup();
        list.add(Bytes32.random());

        UnsignedLong size = list.size();
        assertTrue(size.compareTo(UnsignedLong.ONE) == 0);
    }


    @Test
    void addRemoveTest(){
        UnsignedLongList<Bytes32> list = setup();
        int MAX_SIZE = 5;

        for(int i = 0; i <= MAX_SIZE; i++){
            list.add(true);
        }

        for(int i = 0; i <= MAX_SIZE; i++){
            list.remove(true);
        }

        UnsignedLong size = list.size();
        assertTrue(size.compareTo(UnsignedLong.ZERO) == 0);
    }

    @Test
    void isEmpty() {
        UnsignedLongList<Bytes32> list = setup();
        int MAX_SIZE = 5;

        assertTrue(list.isEmpty());
        for(int i = 0; i <= MAX_SIZE; i++){
            list.add(true);
        }
        assertFalse(list.isEmpty());
        for(int i = 0; i <= MAX_SIZE; i++){
            list.remove(true);
        }
        assertTrue(list.isEmpty());
    }

    @Test
    void contains() {
        UnsignedLongList<Bytes32> list = setup();
        Bytes32 testValue = Bytes32.random();
        assertFalse(list.contains(testValue));
        list.add(testValue);
        assertTrue(list.contains(testValue));
    }

    @Test
    void add() {
        UnsignedLongList<Bytes32> list = setup();
        int MAX_SIZE = 5;

        for(int i = 0; i < MAX_SIZE; i++){
            list.add(true);
        }

        UnsignedLong size = list.size();
        assertTrue(size.compareTo(UnsignedLong.valueOf(5)) == 0);
    }

    @Test
    void remove() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        for(int i = 0; i < entrySize; i++){
            list.add(testValues.get(i));
        }

        list.remove(testValues.get(3));
        assertTrue(list.size().equals(UnsignedLong.valueOf(10-1)));
        assertTrue(list.get(UnsignedLong.valueOf(3)).equals(testValues.get(4)));
    }

    @Test
    void containsAll() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        assertFalse(list.containsAll(testValues));
        for(int i = 0; i < entrySize; i++){
            list.add(testValues.get(i));
        }
        assertTrue(list.containsAll(testValues));
    }

    @Test
    void addAll() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        assertFalse(list.containsAll(testValues));
        list.addAll(testValues);
        assertTrue(list.containsAll(testValues));
    }


    @Test
    void addAll1() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        list.addAll(testValues);
        list.addAll(UnsignedLong.valueOf(5), testValues);
        assertTrue(list.size().equals(UnsignedLong.valueOf(entrySize *2)));
        assertTrue(list.get(UnsignedLong.valueOf(5)).equals(testValues.get(0)));
        assertTrue(list.get(UnsignedLong.valueOf(0)).equals(testValues.get(0)));
        assertTrue(list.get(list.size().minus(UnsignedLong.ONE)).equals(testValues.get(testValues.size()-1)));
        assertTrue(list.get(UnsignedLong.valueOf(14)).equals(testValues.get(testValues.size()-1)));
    }

    @Test
    void removeAll() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();
        List<Bytes32> testValues2 = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            testValues.add(Bytes32.random());
        }
        for(int i = 0; i < 5; i++){
            testValues2.add(Bytes32.random());
        }
        for(int i = 0; i < entrySize; i++){
            list.add(testValues.get(i));
        }
        assertFalse(list.removeAll(testValues2));
        assertTrue(list.removeAll(testValues));
    }

    @Test
    void retainAll() {
    }

    @Test
    void clear() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            testValues.add(Bytes32.random());
        }
        for(int i = 0; i < entrySize; i++){
            list.add(testValues.get(i));
        }
        list.clear();
        assertTrue(list.size().equals(UnsignedLong.ZERO));
        assertTrue(list.get(UnsignedLong.ZERO) == null);
    }

    @Test
    void get() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            testValues.add(Bytes32.random());
        }
        for(int i = 0; i < entrySize; i++){
            list.add(testValues.get(i));
        }
        assertTrue(list.get(UnsignedLong.ZERO).equals(testValues.get(0)));
        list.clear();
        assertTrue(list.get(UnsignedLong.ZERO) == null);
    }

    @Test
    void set() {
        UnsignedLongList<Bytes32> list = setup();
        Bytes32 testValue0 = Bytes32.random();
        Bytes32 testValue1 = Bytes32.random();

        list.add(testValue1);
        list.set(UnsignedLong.ZERO, testValue0);
        list.get(UnsignedLong.ZERO);
        assertTrue(list.get(UnsignedLong.ZERO).equals(testValue0));
        assertTrue(list.size().equals(UnsignedLong.ONE));
    }

    @Test
    void add1() {
        UnsignedLongList<Bytes32> list = setup();
        Bytes32 testValue0 = Bytes32.random();
        Bytes32 testValue1 = Bytes32.random();

        list.add(testValue1);
        list.add(UnsignedLong.ZERO, testValue0);
        assertTrue(list.get(UnsignedLong.ZERO).equals(testValue0));
        assertTrue(list.size().equals(UnsignedLong.valueOf(2)));
    }

    @Test
    void remove1() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        list.addAll(testValues);

        list.remove(UnsignedLong.valueOf(3));
        assertTrue(list.size().equals(UnsignedLong.valueOf(10-1)));
        assertTrue(list.get(UnsignedLong.valueOf(3)).equals(testValues.get(4)));
    }

    @Test
    void indexOf() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        for(int i = 0; i < entrySize; i++){
            list.add(testValues.get(i));
        }

        assertTrue(list.indexOf(testValues.get(0)).equals(UnsignedLong.ZERO));
        list.clear();
        assertTrue(list.indexOf(testValues.get(0)) == null);
    }

    @Test
    void lastIndexOf() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        Bytes32 testValue = Bytes32.random();
        list.add(testValue);
        list.add(testValue);

        assertTrue(list.lastIndexOf(testValue).equals(UnsignedLong.ONE));
        list.clear();
        assertTrue(list.lastIndexOf(testValue) == null);
    }

    @Test
    void subList() {
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        list.addAll(testValues);
        list.addAll(testValues);

        UnsignedLongList newList = list.subList(UnsignedLong.ZERO, UnsignedLong.valueOf(9));
        assertTrue(newList.containsAll(testValues));
        assertTrue(newList.size().equals(UnsignedLong.valueOf(entrySize)));

        assertTrue(list.containsAll(testValues));
        assertTrue(list.size().equals(UnsignedLong.valueOf(entrySize*2)));
    }

    @Test
    void iterator(){
        int entrySize = 10;
        UnsignedLongList<Bytes32> list = setup();
        List<Bytes32> testValues = new ArrayList<Bytes32>();

        for(int i = 0; i < entrySize; i++){
            Bytes32 testValue = Bytes32.random();
            testValues.add(testValue);
        }
        list.addAll(testValues);

        Iterator<Bytes32> itr = list.iterator();

        int i = 0;
        while(itr.hasNext()) {
            Bytes32 bytes = itr.next();
            assertTrue(bytes.equals(testValues.get(i)));
            i++;
        }

        i = 0;
        for(Bytes32 bytes: list){
            assertTrue(bytes.equals(testValues.get(i)));
            i++;
        }
    }
}