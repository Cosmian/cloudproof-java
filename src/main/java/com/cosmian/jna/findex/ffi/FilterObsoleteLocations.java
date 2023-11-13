package com.cosmian.jna.findex.ffi;

import java.util.List;

import com.cosmian.jna.findex.FindexCallbackException;
import com.cosmian.jna.findex.ffi.FindexNativeWrapper.FilterObsoleteLocationsCallback;
import com.cosmian.jna.findex.ffi.FindexUserCallbacks.DBFilterObsoleteLocations;
import com.cosmian.jna.findex.serde.Leb128Reader;
import com.cosmian.jna.findex.serde.Leb128Writer;
import com.cosmian.jna.findex.structs.Location;
import com.cosmian.utils.CloudproofException;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

public class FilterObsoleteLocations implements FilterObsoleteLocationsCallback {

    private DBFilterObsoleteLocations list;

    public FilterObsoleteLocations(DBFilterObsoleteLocations list) {
        this.list = list;
    }

    @Override
    public int apply(Pointer output,
                     IntByReference outputSize,
                     Pointer items,
                     int itemsLength)
        throws CloudproofException {
        try {
            //
            // Read `items` until `itemsLength`
            //
            byte[] itemsBytes = new byte[itemsLength];
            items.read(0, itemsBytes, 0, itemsLength);

            // Locations values are sent, not the indexed value, hence the use of a BytesVector
            List<Location> locations = Leb128Reader.deserializeCollection(Location.class, itemsBytes);

            List<Location> removedLocations = this.list.list(locations);
            byte[] bytes = Leb128Writer.serializeCollection(removedLocations);
            output.write(0, bytes, 0, bytes.length);
            if (removedLocations.size() > 0) {
                outputSize.setValue(bytes.length);
            } else {
                outputSize.setValue(0);
            }
            return 0;
        } catch (CloudproofException e) {
            return FindexCallbackException.record(e);
        }
    }

}
