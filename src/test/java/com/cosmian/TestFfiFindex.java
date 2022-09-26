package com.cosmian;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cosmian.findex.Redis;
import com.cosmian.findex.Sqlite;
import com.cosmian.findex.UsersDataset;
import com.cosmian.jna.findex.Ffi;
import com.cosmian.jna.findex.IndexedValue;
import com.cosmian.jna.findex.Location;
import com.cosmian.jna.findex.MasterKeys;
import com.cosmian.jna.findex.Word;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestFfiFindex {

    @BeforeAll
    public static void before_all() {
        TestUtils.initLogging();
    }

    public byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] passHash = sha256.digest(data);
        return passHash;
    }

    @Test
    public void testUpsertAndSearchSqlite() throws Exception {
        System.out.println("");
        System.out.println("---------------------------------------");
        System.out.println("Findex Upsert Sqlite");
        System.out.println("---------------------------------------");
        System.out.println("");

        //
        // Recover master keys (k and k*)
        //
        String masterKeysJson = Resources.load_resource("findex/keys.json");
        MasterKeys masterKeys = MasterKeys.fromJson(masterKeysJson);

        byte[] label = Resources.load_resource_as_bytes("findex/label");

        //
        // Recover test vectors
        //
        ObjectMapper mapper = new ObjectMapper();
        String expectedSearchResultsInt = Resources.load_resource("findex/expected_db_uids.json");
        int[] expectedDbUids = mapper.readValue(expectedSearchResultsInt, int[].class);
        Arrays.sort(expectedDbUids);

        //
        // Build dataset with DB uids and words
        //
        String dataJson = Resources.load_resource("findex/data.json");
        UsersDataset[] testFindexDataset = UsersDataset.fromJson(dataJson);
        HashMap<IndexedValue, Word[]> indexedValuesAndWords = new HashMap<>();
        for (UsersDataset user : testFindexDataset) {
            ByteBuffer dbuf = ByteBuffer.allocate(32);
            dbuf.putInt(user.id);
            byte[] dbUid = dbuf.array();

            indexedValuesAndWords.put(new Location(dbUid), user.values());
        }

        //
        // Prepare Sqlite tables and users
        //
        Sqlite db = new Sqlite();
        db.insertUsers(testFindexDataset);

        //
        // Upsert
        //
        Ffi.upsert(masterKeys, label, indexedValuesAndWords, db.fetchEntry, db.upsertEntry, db.upsertChain);
        System.out.println("After insertion: entry_table: nb indexes: " + db.getAllKeyValueItems("entry_table").size());
        System.out.println("After insertion: chain_table: nb indexes: " + db.getAllKeyValueItems("chain_table").size());

        //
        // Search
        //
        System.out.println("");
        System.out.println("---------------------------------------");
        System.out.println("Findex Search Sqlite");
        System.out.println("---------------------------------------");
        System.out.println("");

        {
            List<byte[]> indexedValuesList =
                Ffi.search(masterKeys.getK(), label, new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertArrayEquals(expectedDbUids, dbUids);
        }

        // This compact should do nothing except changing the label since the users table didn't change.
        Ffi.compact(1, masterKeys, "NewLabel".getBytes(), db.fetchEntry, db.fetchChain, db.fetchAllEntry,
            db.updateLines, db.listRemovedLocations);

        {
            // Search with old label
            List<byte[]> indexedValuesList =
                Ffi.search(masterKeys.getK(), label, new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertEquals(0, dbUids.length);
        }

        {
            // Search with new label and without user changes
            List<byte[]> indexedValuesList = Ffi.search(masterKeys.getK(), "NewLabel".getBytes(),
                new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertArrayEquals(expectedDbUids, dbUids);
        }

        // Delete the user n°17 to test the compact indexes
        db.deleteUser(17);
        int[] newExpectedDbUids = ArrayUtils.removeElement(expectedDbUids, 17);

        Ffi.compact(1, masterKeys, "NewLabel".getBytes(), db.fetchEntry, db.fetchChain, db.fetchAllEntry,
            db.updateLines, db.listRemovedLocations);

        {
            // Search should return everyone instead of n°17

            List<byte[]> indexedValuesList = Ffi.search(masterKeys.getK(), "NewLabel".getBytes(),
                new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertArrayEquals(newExpectedDbUids, dbUids);
        }

        // Check allocation problem during insertions. Allocation problem could occur when fetching entry table
        // values
        // whose sizes depend on words being indexed: the Entry Table Encrypted value is:
        // `EncSym(𝐾value, (ict_uid𝑥𝑤𝑖, 𝐾𝑤𝑖 , 𝑤𝑖))`
        for (int i = 0; i < 100; i++) {
            Ffi.upsert(masterKeys, label, indexedValuesAndWords, db.fetchEntry, db.upsertEntry, db.upsertChain);
        }
    }

    private static boolean available(int port) {
        System.out.println("--------------Testing port " + port);
        Socket s = null;
        try {
            s = new Socket("localhost", port);
            // If the code makes it this far without an exception it means
            // something is using the port and has responded.
            System.out.println("--------------Port " + port + " is not available");
            return false;
        } catch (IOException e) {
            System.out.println("--------------Port " + port + " is available");
            return true;
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    throw new RuntimeException("Error not handled.", e);
                }
            }
        }
    }

    @Test
    public void testUpsertAndSearchRedis() throws Exception {

        if (!available(6379)) {
            System.out.println("Ignore test since Redis is down");
            return;
        }

        System.out.println("");
        System.out.println("---------------------------------------");
        System.out.println("Findex Upsert Redis");
        System.out.println("---------------------------------------");
        System.out.println("");

        //
        // Recover master keys (k and k*)
        //
        String masterKeysJson = Resources.load_resource("findex/keys.json");
        MasterKeys masterKeys = MasterKeys.fromJson(masterKeysJson);

        byte[] label = Resources.load_resource_as_bytes("findex/label");

        //
        // Recover test vectors
        //
        ObjectMapper mapper = new ObjectMapper();
        String expectedSearchResultsInt = Resources.load_resource("findex/expected_db_uids.json");
        int[] expectedDbUids = mapper.readValue(expectedSearchResultsInt, int[].class);
        Arrays.sort(expectedDbUids);

        //
        // Build dataset with DB uids and words
        //
        String dataJson = Resources.load_resource("findex/data.json");
        UsersDataset[] testFindexDataset = UsersDataset.fromJson(dataJson);
        HashMap<IndexedValue, Word[]> indexedValuesAndWords = new HashMap<>();
        for (UsersDataset user : testFindexDataset) {
            ByteBuffer dbuf = ByteBuffer.allocate(32);
            dbuf.putInt(user.id);
            byte[] dbUid = dbuf.array();

            indexedValuesAndWords.put(new Location(dbUid), user.values());
        }

        //
        // Prepare Sqlite tables and users
        //
        // Sqlite db = new Sqlite();
        // db.insertUsers(testFindexDataset);
        Redis db = new Redis();
        db.jedis.flushAll();
        db.insertUsers(testFindexDataset);

        //
        // Upsert
        //
        Ffi.upsert(masterKeys, label, indexedValuesAndWords, db.fetchEntry, db.upsertEntry, db.upsertChain);
        System.out.println("After insertion: entry_table: nb indexes: "
            + db.getAllKeysAndValues(Redis.INDEX_TABLE_ENTRY_STORAGE).size());
        System.out.println("After insertion: chain_table: nb indexes: "
            + db.getAllKeysAndValues(Redis.INDEX_TABLE_CHAIN_STORAGE).size());

        //
        // Search
        //
        System.out.println("");
        System.out.println("---------------------------------------");
        System.out.println("Findex Search Redis");
        System.out.println("---------------------------------------");
        System.out.println("");

        {
            List<byte[]> indexedValuesList =
                Ffi.search(masterKeys.getK(), label, new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertArrayEquals(expectedDbUids, dbUids);
        }

        // This compact should do nothing except changing the label since the users table didn't change.
        Ffi.compact(1, masterKeys, "NewLabel".getBytes(), db.fetchEntry, db.fetchChain, db.fetchAllEntry,
            db.updateLines, db.listRemovedLocations);

        {
            // Search with old label

            List<byte[]> indexedValuesList =
                Ffi.search(masterKeys.getK(), label, new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertEquals(0, dbUids.length);
        }

        {
            // Search with new label and without user changes

            List<byte[]> indexedValuesList = Ffi.search(masterKeys.getK(), "NewLabel".getBytes(),
                new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertArrayEquals(expectedDbUids, dbUids);
        }

        // Delete the user n°17 to test the compact indexes
        db.deleteUser(17);
        int[] newExpectedDbUids = ArrayUtils.removeElement(expectedDbUids, 17);

        Ffi.compact(1, masterKeys, "NewLabel".getBytes(), db.fetchEntry, db.fetchChain, db.fetchAllEntry,
            db.updateLines, db.listRemovedLocations);

        {
            // Search should return everyone instead of n°17

            List<byte[]> indexedValuesList = Ffi.search(masterKeys.getK(), "NewLabel".getBytes(),
                new Word[] {new Word("France")}, 0, db.fetchEntry, db.fetchChain);
            int[] dbUids = indexedValuesBytesListToArray(indexedValuesList);

            assertArrayEquals(newExpectedDbUids, dbUids);
        }

        // Check allocation problem during insertions. Allocation problem could occur when fetching entry table
        // values
        // whose sizes depend on words being indexed: the Entry Table Encrypted value is:
        // `EncSym(𝐾value, (ict_uid𝑥𝑤𝑖, 𝐾𝑤𝑖 , 𝑤𝑖))`
        for (int i = 0; i < 100; i++) {
            Ffi.upsert(masterKeys, label, indexedValuesAndWords, db.fetchEntry, db.upsertEntry, db.upsertChain);
        }
    }

    /*
     * Helper function to transform the list of bytes returned by the FFI (representing `IndexedValue`) to a sorted
     * array of int (representing the DB id of users).
     */
    private int[] indexedValuesBytesListToArray(List<byte[]> indexedValuesList) throws Exception {
        int[] dbUids = new int[indexedValuesList.size()];
        int count = 0;
        for (byte[] dbUidBytes : indexedValuesList) {
            byte[] location = new IndexedValue(dbUidBytes).getLocation().getBytes();

            dbUids[count] = ByteBuffer.wrap(location).getInt();
            count++;
        }

        Arrays.sort(dbUids);
        return dbUids;
    }

}
