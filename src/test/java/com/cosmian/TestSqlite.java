package com.cosmian;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cosmian.findex.Sqlite;
import com.cosmian.jna.findex.serde.Tuple;
import com.cosmian.jna.findex.structs.EntryTableValue;
import com.cosmian.jna.findex.structs.Uid32;

public class TestSqlite {

    @BeforeAll
    public static void before_all() {
        TestUtils.initLogging();
    }

    @Test
    public void testConditionalUpsert() throws Exception {
        System.out.println("Sqlite conditional upsert");
        try (Sqlite db = new Sqlite()) {
            String tableName = "test_table";
            Statement stat = db.getConnection().createStatement();
            stat.execute("DROP TABLE IF EXISTS " + tableName + ";");
            stat.execute("CREATE TABLE " + tableName + " (uid BLOB PRIMARY KEY,value BLOB NOT NULL);");

            // generate some random uid and values
            Random rand = new Random();
            byte[] uidBuffer = new byte[32];
            byte[] valueBuffer = new byte[64];
            int NEW_TOTAL = 999;
            HashMap<Uid32, Tuple<EntryTableValue, EntryTableValue>> originalValues = new HashMap<>();
            HashMap<Uid32, Tuple<EntryTableValue, EntryTableValue>> updatedValues = new HashMap<>();
            for (int i = 0; i < NEW_TOTAL; i++) {
                rand.nextBytes(uidBuffer);
                Uid32 uid = new Uid32(Arrays.copyOf(uidBuffer, uidBuffer.length));
                rand.nextBytes(valueBuffer);
                Tuple<EntryTableValue, EntryTableValue> tuple = new Tuple<>(
                    new EntryTableValue(new byte[] {}),
                    new EntryTableValue(Arrays.copyOf(valueBuffer, valueBuffer.length)));
                if (i % 3 == 0) {
                    updatedValues.put(uid, tuple);
                } else {
                    originalValues.put(uid, tuple);
                }
            }

            System.out.println(" .  Original Values: " + originalValues.size());
            System.out.println(" .  Updated  Values: " + updatedValues.size());

            // insert originals
            Map<Uid32, byte[]> failed = db.conditionalUpsert(originalValues, tableName);
            assertEquals(0, failed.size());

            // the number of entries that should fail on update
            int numOverlapSuccess = rand.nextInt(NEW_TOTAL / 3);
            // the number of entries that should succeed in update
            int numOverlapFail = rand.nextInt(NEW_TOTAL / 3);

            System.out.println(" .   + success: " + numOverlapSuccess);
            System.out.println(" .   + failed : " + numOverlapFail);

            int counterSuccess = 0;
            int counterFail = 0;
            for (Entry<Uid32, Tuple<EntryTableValue, EntryTableValue>> entry : originalValues.entrySet()) {
                rand.nextBytes(valueBuffer);
                EntryTableValue newValue = new EntryTableValue(Arrays.copyOf(valueBuffer, valueBuffer.length));
                if (++counterSuccess <= numOverlapSuccess) {
                    updatedValues.put(
                        entry.getKey(),
                        new Tuple<>(entry.getValue().getRight(), newValue));
                } else if (++counterFail <= numOverlapFail) {
                    updatedValues.put(
                        entry.getKey(),
                        new Tuple<>(
                            new EntryTableValue(new byte[] {'f', 'a', 'i', 'l'}), newValue));
                } else {
                    break;
                }
            }

            // insert updated
            failed = db.conditionalUpsert(updatedValues, "test_table");
            assertEquals(numOverlapFail, failed.size());

            // cleanup
            stat.execute("DROP TABLE IF EXISTS " + tableName + ";");

            System.out.println("<== success");
        }

    }

}
