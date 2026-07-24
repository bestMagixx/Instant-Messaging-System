package com.lld.im.common.route.algorithm.consistenthash;

import com.lld.im.common.enums.UserErrorCode;
import com.lld.im.common.exception.ApplicationException;

import java.util.SortedMap;
import java.util.TreeMap;

public class TreeMapConsistentHash extends AbstractConsistentHash{

    private TreeMap<Long, String> treeMap = new TreeMap<>();

    @Override
    protected void add(long key, String value) {
        treeMap.put(super.hash(key+""),value);
    }

    @Override
    protected String getFirstNodeValue(String value) {
        Long hash = super.hash(value);
        SortedMap<Long, String> last = treeMap.tailMap(hash);
        if(!last.isEmpty()){
            return last.get(last.firstKey());
        }

        if(treeMap.isEmpty()){
            throw new ApplicationException(UserErrorCode.SERVER_NOT_AVAILABLE);
        }

        return treeMap.firstEntry().getValue();
    }

    @Override
    protected void processBefore() {
        treeMap.clear();
    }
}
