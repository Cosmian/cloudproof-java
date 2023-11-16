package com.cosmian.jna.findex;

import java.util.List;
import java.util.Map;

import com.cosmian.jna.findex.serde.Leb128Reader;
import com.cosmian.jna.findex.serde.Tuple;
import com.cosmian.jna.findex.structs.ChainTableValue;
import com.cosmian.jna.findex.ffi.FindexNativeWrapper.FetchCallback;
import com.cosmian.jna.findex.ffi.FindexNativeWrapper.InsertCallback;
import com.cosmian.jna.findex.ffi.FFiUtils;
import com.cosmian.jna.findex.ffi.FindexNativeWrapper.DeleteCallback;
import com.cosmian.jna.findex.structs.Uid32;
import com.cosmian.utils.CloudproofException;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public interface ChainTableDatabase {

    /**
     * Fetch the Chain Table lines for the list of given {@link Uid32}. If a line does not exist, there should be no
     * entry in the returned map.
     * <p>
     * Implementation of this method is always required (to search, update or compact the index)
     *
     * @param uids the unique {@link Uid32}s used as line id
     * @return a {@link Map} of {@link Uid32} to {@link ChainTableValue}
     * @throws CloudproofException if anything goes wrong
     */
    public List<Tuple<Uid32, ChainTableValue>> fetch(List<Uid32> uids) throws CloudproofException;

    /**
     * Upsert the given lines into the Chain Table.
     * <p>
     * The {@link ChainTableValues} structure contains both the new value to be upserted and the previous value known at
     * the time of fetch. To avoid concurrency issues, the new value of an existing {@link Uid32} must <b>not</b> be
     * updated if the current value in the database does not match the previous value of the structure. In such a case,
     * the {@link Uid32} and the <b>current</b> database value must be returned as part of the returned {@link Map}. *
     * <p>
     * Implementation of this method is only required to update or compact the index
     * <p>
     * See the Redis and Sqlite implementations for implementation examples
     * <p>
     *
     * @param uidsAndValues a {@link Map} of {@link Uid32} to {@link ChainTableValues}
     * @return a map of the {@link Uid32} that could not be updated and the current database value for the entry.
     * @throws CloudproofException if anything goes wrong
     */
    public void insert(Map<Uid32, ChainTableValue> uidsAndValues)
	throws CloudproofException;

    public void delete(List<Uid32> uids)
	throws CloudproofException;

    /**
     * Return the appropriate fetch callback (with input/output serialization).
     */
    default FetchCallback fetchCallback() {
        return new FetchCallback() {
            @Override
            public int callback(Pointer output, IntByReference outputLen, Pointer uidsPtr, int uidsLength)
	    {
		try {
                    byte[] uids = new byte[uidsLength];
                    uidsPtr.read(0, uids, 0, uidsLength);
                    List<Uid32> chainTableUids = Leb128Reader.deserializeCollection(Uid32.class, uids);
                    List<Tuple<Uid32, ChainTableValue>> uidsAndValues = fetch(chainTableUids);
                    return FFiUtils.listOfTuplesToOutputPointer(uidsAndValues, output, outputLen);
                } catch (CloudproofException e) {
                    return FindexCallbackException.record(e);
                }
            }
        };
    }

    /**
     * Return the appropriate insert callback (with input/output serialization).
     */
    default InsertCallback insertCallback() {
        return new InsertCallback() {
            @Override
            public int callback(Pointer items, int itemsLength) {
                try {
                    //
                    // Read `items` until `itemsLength`
                    //
                    byte[] itemsBytes = new byte[itemsLength];
                    items.read(0, itemsBytes, 0, itemsLength);

                    //
                    // Deserialize the chain table items
                    //
                    Map<Uid32, ChainTableValue> uidsAndValues =
                        Leb128Reader.deserializeMap(Uid32.class, ChainTableValue.class, itemsBytes);

                    //
                    // Insert in database
                    //
                    insert(uidsAndValues);

                    return 0;
                } catch (CloudproofException e) {
                    return FindexCallbackException.record(e);
                }
            }
        };
    }

    /**
     * Return the appropriate delete callback (with input/output serialization).
     */
    default DeleteCallback deleteCallback() {
        return new DeleteCallback() {
            @Override
            public int callback(Pointer items, int itemsLength) {
                try {
                    //
                    // Read `items` until `itemsLength`
                    //
                    byte[] itemsBytes = new byte[itemsLength];
                    items.read(0, itemsBytes, 0, itemsLength);

                    //
                    // Deserialize the chain table items
                    //
                    List<Uid32> uids = Leb128Reader.deserializeCollection(Uid32.class, itemsBytes);

                    //
                    // Insert in database
                    //
                    delete(uids);

                    return 0;
                } catch (CloudproofException e) {
                    return FindexCallbackException.record(e);
                }
	    }
	};
    }
}
