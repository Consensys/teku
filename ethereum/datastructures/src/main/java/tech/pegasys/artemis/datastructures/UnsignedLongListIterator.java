package tech.pegasys.artemis.datastructures;

import com.google.common.primitives.UnsignedLong;

import java.util.Iterator;

public class UnsignedLongListIterator implements Iterator {
    UnsignedLongList list;
    UnsignedLong currentIndex;

    public UnsignedLongListIterator(UnsignedLongList list) {
        this.list = list;
        this.currentIndex = UnsignedLong.ZERO;
    }

    @Override
    public boolean hasNext() {
        if(!list.size().equals(UnsignedLong.ZERO) && list.size().compareTo(this.currentIndex.plus(UnsignedLong.ONE)) > 0) return true;
        return false;
    }

    @Override
    public Object next() {
        if(hasNext()){
            Object obj = list.get(currentIndex);
            currentIndex = currentIndex.plus(UnsignedLong.ONE);
            return obj;
        }
        return null;
    }
}