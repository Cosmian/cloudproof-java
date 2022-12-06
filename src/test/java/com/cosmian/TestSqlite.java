package com.cosmian;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cosmian.findex.Sqlite;
import com.cosmian.jna.findex.FindexWrapper.EntryTableValue;
import com.cosmian.jna.findex.Uid;

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
            HashMap<Uid, EntryTableValue> originalValues = new HashMap<>();
            HashMap<Uid, EntryTableValue> updatedValues = new HashMap<>();
            for (int i = 0; i < NEW_TOTAL; i++) {
                rand.nextBytes(uidBuffer);
                Uid uid = new Uid(Arrays.copyOf(uidBuffer, uidBuffer.length));
                rand.nextBytes(valueBuffer);
                if (i % 3 == 0) {
                    updatedValues.put(uid,
                            new EntryTableValue(new byte[] {}, Arrays.copyOf(valueBuffer, valueBuffer.length)));
                } else {
                    originalValues.put(uid,
                            new EntryTableValue(new byte[] {}, Arrays.copyOf(valueBuffer, valueBuffer.length)));
                }
            }

            System.out.println(" .  Original Values: " + originalValues.size());
            System.out.println(" .  Updated  Values: " + updatedValues.size());

            // insert originals
            Set<Uid> fails = db.conditionalUpsert(originalValues, tableName);
            assertEquals(0, fails.size());

            // the number of entries that should fail on update
            int numOverlapSuccess = rand.nextInt(NEW_TOTAL / 3);
            // the number of entries that should succeed in update
            int numOverlapFail = rand.nextInt(NEW_TOTAL / 3);

            System.out.println(" .   + success: " + numOverlapSuccess);
            System.out.println(" .   + failed : " + numOverlapFail);

            int counterSuccess = 0;
            int counterFail = 0;
            for (Entry<Uid, EntryTableValue> entry : originalValues.entrySet()) {
                if (++counterSuccess <= numOverlapSuccess) {
                    rand.nextBytes(valueBuffer);
                    updatedValues.put(entry.getKey(), new EntryTableValue(entry.getValue().newValue, valueBuffer));
                } else if (++counterFail <= numOverlapFail) {
                    rand.nextBytes(valueBuffer);
                    updatedValues.put(entry.getKey(),
                            new EntryTableValue(new byte[] { 'f', 'a', 'i', 'l' }, valueBuffer));
                } else {
                    break;
                }
            }

            // insert updated
            fails = db.conditionalUpsert(updatedValues, "test_table");
            assertEquals(numOverlapFail, fails.size());
            System.out.println("<== success");
        }

    }

}
