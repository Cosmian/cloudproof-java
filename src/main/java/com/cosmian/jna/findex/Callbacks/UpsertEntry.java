package com.cosmian.jna.findex.Callbacks;

import java.util.Map;

import com.cosmian.CloudproofException;
import com.cosmian.jna.findex.EntryTableValue;
import com.cosmian.jna.findex.EntryTableValues;
import com.cosmian.jna.findex.Findex;
import com.cosmian.jna.findex.FindexWrapper.UpsertEntryCallback;
import com.cosmian.jna.findex.FindexWrapper.UpsertEntryInterface;
import com.cosmian.jna.findex.Uid;
import com.cosmian.jna.findex.serde.Leb128Reader;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public class UpsertEntry implements UpsertEntryCallback {

    private UpsertEntryInterface upsert;

    public UpsertEntry(UpsertEntryInterface upsert) {
        this.upsert = upsert;
    }

    @Override
    public int apply(Pointer entries,
                     int entriesLength,
                     Pointer outputs,
                     IntByReference outputsLength)
        throws CloudproofException {
        //
        // Read `entries` until `itemsLength`
        //
        byte[] entriesBytes = new byte[entriesLength];
        entries.read(0, entriesBytes, 0, entriesLength);

        Map<Uid, EntryTableValues> map =
            Leb128Reader.deserializeMap(Uid.class, EntryTableValues.class, entriesBytes);

        Map<Uid, EntryTableValue> failedEntries = upsert.upsert(map);
        return Findex.mapToOutputPointer(failedEntries, outputs, outputsLength);

    }

}
