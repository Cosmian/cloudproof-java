package com.cosmian.findex;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cosmian.jna.findex.Database;
import com.cosmian.jna.findex.serde.Tuple;
import com.cosmian.jna.findex.structs.ChainTableValue;
import com.cosmian.jna.findex.structs.EntryTableValue;
import com.cosmian.jna.findex.structs.EntryTableValues;
import com.cosmian.jna.findex.structs.IndexedValue;
import com.cosmian.jna.findex.structs.Location;
import com.cosmian.jna.findex.structs.Uid32;
import com.cosmian.utils.CloudproofException;

public class MultiSqlite extends Database {

    private final List<Sqlite> dbList;

    public MultiSqlite(List<Sqlite> dbList) throws SQLException {
        this.dbList = dbList;
    }

    @Override
    protected List<Tuple<Uid32, EntryTableValue>> fetchEntries(List<Uid32> uids) throws CloudproofException {
        List<Tuple<Uid32, EntryTableValue>> output = new ArrayList<Tuple<Uid32, EntryTableValue>>();
        for (Sqlite sqlite : this.dbList) {
            output.addAll(sqlite.fetchEntries(uids));
        }

        return output;
    }

    @Override
    protected List<Tuple<Uid32, ChainTableValue>> fetchChains(List<Uid32> uids) throws CloudproofException {
        List<Tuple<Uid32, ChainTableValue>> output = new ArrayList<Tuple<Uid32, ChainTableValue>>();
        for (Sqlite sqlite : this.dbList) {
            output.addAll(sqlite.fetchChains(uids));
        }
        return output;
    }

    @Override
    protected boolean searchProgress(List<IndexedValue> indexedValues) throws CloudproofException {
        // let search progress
        return true;
    }

    @Override
    protected List<Location> listRemovedLocations(List<Location> locations) throws CloudproofException {
        throw new CloudproofException("not implemented");
    }


    protected Set<Uid32> fetchAllEntryTableUids() throws CloudproofException {
        throw new CloudproofException("not implemented");
    }

    @Override
    protected Map<Uid32, EntryTableValue> upsertEntries(Map<Uid32, EntryTableValues> uidsAndValues)
        throws CloudproofException {
        throw new CloudproofException("not implemented");
    }

    @Override
    protected void upsertChains(Map<Uid32, ChainTableValue> uidsAndValues) throws CloudproofException {
        throw new CloudproofException("not implemented");
    }

    @Override
    protected void updateTables(List<Uid32> removedChains,
                                Map<Uid32, EntryTableValue> newEntries,
                                Map<Uid32, ChainTableValue> newChains)
        throws CloudproofException {
        throw new CloudproofException("not implemented");
    }

}
