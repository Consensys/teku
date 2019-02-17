package tech.pegasys.artemis.datastructures;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UnsignedLongList<E> implements Iterable<E> {
    public static UnsignedLong MAX_SIZE = UnsignedLong.MAX_VALUE;
    HashMap<UnsignedLong, Object> hashList;

    UnsignedLong index = null;

    public UnsignedLongList() {
        hashList = new HashMap<UnsignedLong, Object>();
    }

    public UnsignedLong size() {
        if(this.index == null) return UnsignedLong.ZERO;
        return this.index.plus(UnsignedLong.ONE);
    }

    public boolean isEmpty() {
        return size().equals(UnsignedLong.ZERO);
    }

    public boolean contains(Object o) {
        for (Map.Entry<UnsignedLong, Object> entry : hashList.entrySet()) {
            if (o.equals(entry.getValue())) return true;
        }
        return false;
    }

    public boolean add(Object o) {
        if(this.index == null) this.index = UnsignedLong.ZERO;
        else this.index = this.index.plus(UnsignedLong.ONE);
        if(size().compareTo(MAX_SIZE) <= 0){
            hashList.put(this.index, o);
            return true;
        }
        this.index = this.index.minus(UnsignedLong.ONE);
        return false;
    }

    public boolean remove(Object o) {
        UnsignedLong key = indexOf(o);
        if(key != null){
            remove(key);
            return true;
        }
        return false;
    }

    public boolean containsAll(@NotNull Collection c) {
        List resultList = new ArrayList();
        for (Map.Entry<UnsignedLong, Object> entry : hashList.entrySet()) {
            for(Object o: c){
                if(entry.getValue().equals(o))resultList.add(o);
            }
        }
        return !resultList.isEmpty();
    }

    public boolean addAll(@NotNull Collection c) {
        boolean returnValue = false;
        for(Object o: c){
            if(!add(o)) returnValue = true;
        }
        return returnValue;
    }

    @VisibleForTesting
    public static void setMaxSize(UnsignedLong maxSize) {
        MAX_SIZE = maxSize;
    }

    public boolean addAll(UnsignedLong index, @NotNull Collection c) {
        UnsignedLong originalSize = UnsignedLong.valueOf(hashList.entrySet().size());
        for(Object o: c){
            add(index, o);
            index = index.plus(UnsignedLong.ONE);
        }
        return (originalSize.compareTo(size()) < 0);
    }

    public boolean removeAll(@NotNull Collection c) {
        List<UnsignedLong> keys = new ArrayList<UnsignedLong>();
        for (Map.Entry<UnsignedLong, Object> entry : hashList.entrySet()) {
            for(Object o: c){
                if (o.equals(entry.getValue())) keys.add(entry.getKey());
            }
        }
        for(UnsignedLong key: keys) remove(key);
        return (keys.size() > 0);
    }


    public boolean retainAll(@NotNull Collection c) {
        UnsignedLong swapSize = UnsignedLong.ZERO;
        HashMap<UnsignedLong, Object> swap = new HashMap<UnsignedLong, Object>();
        for (Map.Entry<UnsignedLong, Object> entry : hashList.entrySet()) {
            for(Object o: c){
                if(entry.getValue().equals(o)){
                    swapSize.plus(UnsignedLong.ONE);
                    swap.put(swapSize,entry.getValue());
                }
            }
        }
        hashList = swap;
        return (hashList.keySet().size() > 0);
    }

    public void clear() {
        hashList.clear();
        index = null;
    }

    public Object get(UnsignedLong index) {
        return hashList.get(index);
    }


    public Object set(UnsignedLong index, Object element) {
        hashList.remove(index);
        return hashList.put(index, element);
    }

    public void add(UnsignedLong index, Object element) {
        if(hashList.get(index) != null && !size().equals(UnsignedLong.ZERO)){
            Object obj = hashList.remove(index);
            add(index.plus(UnsignedLong.ONE), obj);
            hashList.put(index, element);
        }
        else {
            add(element);
        }
    }

    public Object remove(UnsignedLong index) {
        Object obj = hashList.remove(index);
        if(index.compareTo(this.index) < 0){
            for(UnsignedLong i = index.plus(UnsignedLong.ONE); i.compareTo(this.index) < 0; i = i.plus(UnsignedLong.ONE) ){
                hashList.put(i.minus(UnsignedLong.ONE), hashList.remove(i));
            }
        }
        this.index = this.index.minus(UnsignedLong.ONE);
        return obj;
    }

    public UnsignedLong indexOf(Object o) {
        for (Map.Entry<UnsignedLong, Object> entry : hashList.entrySet()) {
            if (o.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public UnsignedLong lastIndexOf(Object o) {
        UnsignedLong key = null;
        for (Map.Entry<UnsignedLong, Object> entry : hashList.entrySet()) {
            if (o.equals(entry.getValue()) && (key == null || key.compareTo(entry.getKey()) < 0)) key = entry.getKey();
        }
        return key;
    }


    @NotNull
    public UnsignedLongList subList(UnsignedLong fromIndex, UnsignedLong toIndex) {
        UnsignedLongList newList = new UnsignedLongList();
        //if(fromIndex.compareTo(toIndex) <= 0) return newList;
        for(UnsignedLong i = fromIndex; i.compareTo(toIndex) <= 0; i = i.plus(UnsignedLong.ONE) ){
            newList.add(hashList.get(i));
        }
        return newList;
    }

    public Iterator iterator(){
        return new UnsignedLongListIterator(this);
    }

}