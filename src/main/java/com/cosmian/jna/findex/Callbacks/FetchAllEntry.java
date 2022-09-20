package com.cosmian.jna.findex.Callbacks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import com.cosmian.jna.FfiException;
import com.cosmian.jna.findex.FfiWrapper.FetchAllEntryCallback;
import com.cosmian.jna.findex.FfiWrapper.FetchAllEntryInterface;
import com.cosmian.jna.findex.Leb128Serializer;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public class FetchAllEntry implements FetchAllEntryCallback {
    private FetchAllEntryInterface fetch;

    private Iterator<Entry<byte[], byte[]>> entrySetIterator;

    public FetchAllEntry(FetchAllEntryInterface fetch) {
        this.fetch = fetch;
        entrySetIterator = null;
    }

    @Override
    public int apply(Pointer output, IntByReference outputSize, int numberOfEntries) throws FfiException {
        if (entrySetIterator == null) {
            entrySetIterator = this.fetch.fetch().entrySet().iterator();
        }

        Set<Entry<byte[], byte[]>> chunk = new HashSet();
        while (chunk.size() < numberOfEntries && entrySetIterator.hasNext()) {
            chunk.add(entrySetIterator.next());
        }

        if (chunk.size() > 0) {
            byte[] uidsAndValuesBytes = Leb128Serializer.serializeEntrySet(chunk);
            output.write(0, uidsAndValuesBytes, 0, uidsAndValuesBytes.length);
            outputSize.setValue(uidsAndValuesBytes.length);
        } else {
            outputSize.setValue(0);
        }

        return entrySetIterator.hasNext() ? 1 : 0;
    }

}
