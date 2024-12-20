/* This file is part of VoltDB.
 * Copyright (C) 2008-2015 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.regressionsuites;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Random;

import org.voltdb.BackendTarget;
import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.compiler.VoltProjectBuilder;
import org.voltdb.types.TimestampType;

public class TestApproxCountDistinctSuite extends RegressionSuite {

    private static final double ALLOWED_PERCENT_ERROR = 1.5;

    private static final String COLUMN_NAMES[] = {
            "bi",
            "ii",
            "si",
            "ti",
            "ff",
            "dd",
            "ts"
    };

    private static final String TABLE_NAMES[] = {"r", "p"};

    private static long getNormalValue(Random r, double magnitude, long min, long max) {
        double d;
        do {
            d = r.nextGaussian() * magnitude;
        } while (d > max || d <= min);

        return (long) d;
    }

    private static BigDecimal getNormalDecimalValue(Random r, double magnitude) {
        double d;
        do {
            d = r.nextGaussian() * magnitude;
        } while (d > (magnitude * 2) || d <= (-magnitude * 2));

        return new BigDecimal(String.format("%.12f", d));
    }

    private static void fillTable(Client client, String tbl) throws Exception {
        Random r = new Random(777);

        // Insert 1000 rows of data, and 84 (every 13th row) of nulls.
        for (int i = 0; i < 1084; ++i) {

            // Every 13th row, insert null values, just to make sure
            // it doesn't mess with the algorithm.
            if (i % 13 == 0) {
                client.callProcedure(tbl + ".Insert", i,
                        null, null, null, null, null, null, null);
            }
            else {
                // Use a a Gaussian distribution (bell curve), to exercise the hyperloglog hash.
                final long baseTs = 1437589323966000L; // July 22, 2015 or so
                client.callProcedure(tbl + ".Insert",
                        i,    // primary key
                        getNormalValue(r, 1000, Long.MIN_VALUE, Long.MAX_VALUE),
                        getNormalValue(r, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        getNormalValue(r, 1000, Short.MIN_VALUE, Short.MAX_VALUE),
                        getNormalValue(r, 100, Byte.MIN_VALUE, Byte.MAX_VALUE),
                        r.nextGaussian(), // float
                        getNormalDecimalValue(r, 1000000000), // decimal
                        new TimestampType(baseTs + getNormalValue(r, 10000, Short.MIN_VALUE, Short.MAX_VALUE)));
            }
        }
    }

    private static void assertEstimateWithin(String col, long exact, double estimate, double maxError) {
        double percentError = Math.abs(((exact - estimate) / exact) * 100.0);

        /* Uncomment this if you are curious about how accurate the estimates are
        System.out.println(String.format("  %s: Percent error: %2.2f%% (Exact: %5d, Estimate: %4.2f)",
                col, percentError, exact, estimate));
        // */

        assertTrue("Estimate for distinct values in " + col,
                percentError < maxError);
    }

    /**
     * Given two tables assert that cardinality estimates are within
     * some percentage of the exact cardinality.  Relevant data is assumed to
     * be in the last column of the table, after any group by keys.
     * @param col            -- for error reporting
     * @param exactTable     -- table containing exact values
     * @param estimateTable  -- table containing estimates
     * @param maxError       -- Maximum allowed error percentage
     */
    private static void assertEstimatesAreWithin(String col, VoltTable exactTable, VoltTable estimateTable, double maxError) {
        final int whichCol = exactTable.getColumnCount() - 1;

        while(estimateTable.advanceRow()) {
            assertTrue(exactTable.advanceRow());

            assertEstimateWithin(col, exactTable.getLong(whichCol), estimateTable.getDouble(whichCol), maxError);
        }

        assertFalse(exactTable.advanceRow());
    }

    public void testAsTableAgg() throws Exception
    {
        Client client = getClient();

        // lock down some precise values here
        // (rather than comparing with "count(distinct col)")
        // just so we have an idea of what causes these values to change.
        //
        // In other tests, we can just verify that the error in the estimate
        // is reasonably bounded, by computing an exact answer.
        double expectedEstimates[] = {
                859.613,
                862.6527,
                870.759,
                247.4666,
                997.5537,
                1001.615,
                983.3405
        };

        // If there's zero values, then cardinality estimate should be 0.0.
        for (int colIdx = 0; colIdx < COLUMN_NAMES.length; ++colIdx) {
            for (int tblIdx = 0; tblIdx < TABLE_NAMES.length; ++tblIdx) {
                String tbl = TABLE_NAMES[tblIdx];
                String col = COLUMN_NAMES[colIdx];

                String approxStmt = String.format("select approx_count_distinct(%s) from %s", col, tbl);
                VoltTable vt = client.callProcedure("@AdHoc", approxStmt).getResults()[0];
                assertTrue(vt.advanceRow());
                assertEquals(0.0, vt.getDouble(0));
                assertFalse(vt.advanceRow());
            }
        }

        fillTable(client, "p");
        fillTable(client, "r");

        for (int tblIdx = 0; tblIdx < TABLE_NAMES.length; ++tblIdx) {
            for (int colIdx = 0; colIdx < COLUMN_NAMES.length; ++colIdx) {

                String tbl = TABLE_NAMES[tblIdx];
                String col = COLUMN_NAMES[colIdx];

                String approxStmt = String.format("select approx_count_distinct(%s) from %s", col, tbl);

                VoltTable vt = client.callProcedure("@AdHoc", approxStmt).getResults()[0];
                assertTrue(vt.advanceRow());
                double actualEstimate = vt.getDouble(0);
                assertEquals("Actual estimate not expected for column " + col,
                        expectedEstimates[colIdx], actualEstimate, 0.01);
                assertFalse(vt.advanceRow());

                // If we filter out the null values, the answer should be exactly the same
                String approxStmtNoNulls = String.format(
                        "select approx_count_distinct(%s) "
                        + "from %s "
                        + "where %s is not null", col, tbl, col);
                vt = client.callProcedure("@AdHoc", approxStmtNoNulls).getResults()[0];
                assertTrue(vt.advanceRow());
                double actualEstimateNoNulls = vt.getDouble(0);
                assertEquals(actualEstimate, actualEstimateNoNulls, 0.0);
                assertFalse(vt.advanceRow());

                // Compare with the exact distinct count
                String exactStmt = String.format("select count(distinct %s) from %s", col, tbl);

                vt = client.callProcedure("@AdHoc", exactStmt).getResults()[0];
                assertTrue(vt.advanceRow());
                long exact = vt.getLong(0);

                assertEstimateWithin(col, exact, actualEstimate, ALLOWED_PERCENT_ERROR);

                assertFalse(vt.advanceRow());
            }
        }
    }

    /**
     * Run a query against a series of tables and columns to get both approximate
     * and exact distinct cardinality, and compare the two.
     *
     * @param client      database client, assumes tables are loaded
     * @param tables
     * @param columns
     * @param queryFormat  a printf-style string with three embedded %s markers:
     *                       - a place to put "count( distinct" or "approx_count_distinct("
     *                       - a column name
     *                       - a table name
     * @throws Exception
     */
    public void compareEstimateAndExact(Client client, String tables[], String columns[], String queryFormat) throws Exception
    {
        for (int tblIdx = 0; tblIdx < tables.length; ++tblIdx) {
            for (int colIdx = 0; colIdx < columns.length; ++colIdx) {
                String tbl = tables[tblIdx];
                String col = columns[colIdx];

                String approxStmt = String.format(queryFormat,
                        "approx_count_distinct(",
                        col, tbl);
                String exactStmt = String.format(queryFormat,
                        "count( distinct ",
                        col, tbl);

                VoltTable estimateTable = client.callProcedure("@AdHoc", approxStmt).getResults()[0];
                VoltTable exactTable = client.callProcedure("@AdHoc", exactStmt).getResults()[0];

                assertEstimatesAreWithin(col, exactTable, estimateTable, ALLOWED_PERCENT_ERROR);
            }
        }
    }

    public void testAsGroupByAgg() throws Exception
    {
        Client client = getClient();

        fillTable(client, "p");
        fillTable(client, "r");

        // Groups by the low 4 bits of the primary key
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select bitand(cast(pk as bigint), x'03') as pk_lobits, %s %s ) "
                + "from %s "
                + "group by pk_lobits "
                + "order by pk_lobits");

        // Test with another aggregate that cannot be pushed down
        // (all rows will be sent to coordinator, will lock down
        // this behavior with an equivalent planner test.)
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select "
                + "  bitand(cast(pk as bigint), x'03') as pk_lobits, "
                + "  count(distinct bi), "
                + "  %s %s ) "
                + "from %s "
                + "group by pk_lobits "
                + "order by pk_lobits");

        // Test with another aggregate that can be pushed down
        // (approx_count_distinct will be distributed)
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select "
                + "  bitand(cast(pk as bigint), x'03') as pk_lobits, "
                + "  count(dd), "
                + "  %s %s ) "
                + "from %s "
                + "group by pk_lobits "
                + "order by pk_lobits");
    }

    public void testWithPartitionKey() throws Exception {
        Client client = getClient();

        fillTable(client, "p");

        compareEstimateAndExact(client, new String[] {"p"}, new String[] {"pk"},
                "select %s %s ) from %s;");

        compareEstimateAndExact(client, new String[] {"p"}, new String[] {"pk"},
                "select %s %s ) from %s where mod(pk, 3) = 0;");

        compareEstimateAndExact(client, new String[] {"p"}, new String[] {"pk"},
                "select bitand(bi, x'05') as gbk, %s %s ) "
                + "from %s "
                + "group by gbk order by gbk;");

        compareEstimateAndExact(client, new String[] {"p"}, new String[] {"pk"},
                "select bitand(bi, x'05') as gbk, %s %s ) "
                + "from %s "
                + "where mod(pk, 3) = 0 "
                + "group by gbk order by gbk;");
    }

    public void testWithSubqueries() throws Exception {
        Client client = getClient();

        fillTable(client, "p");
        fillTable(client, "r");

        // Try a query where there may be multiple approx_count_distinct aggs,
        // perhaps distributed, perhaps not.
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select %s %s ) "
                + "from (select approx_count_distinct(ii) from r) as repl_subquery,"
                + "  %s");

        // As above but with reorder from clause elements
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select %s %s ) "
                + "from %s, "
                + "(select approx_count_distinct(ii) from r) as repl_subquery");

        // As above but with other aggregates.
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select count(distinct bi), %s %s ) "
                + "from (select sum(dd), approx_count_distinct(ii) from r) as repl_subquery,"
                + "  %s");

        // As above but with other aggregates.
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select count(bi), %s %s ) "
                + "from (select sum(distinct dd), approx_count_distinct(ii) from r) as repl_subquery,"
                + "  %s");

        // This time let's put the distributed approx_count_distinct in the inner query
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select approx_count_distinct(bi), subq.a_count "
                + "from (select %s %s ) as a_count from %s) as subq,"
                + "  r "
                + "group by subq.a_count");

        // As above, but break push-down-ability inner query's agg.
        compareEstimateAndExact(client, TABLE_NAMES, COLUMN_NAMES,
                "select approx_count_distinct(bi), subq.a_count "
                + "from (select %s %s ) as a_count, sum(distinct ii) from %s) as subq,"
                + "  r "
                + "group by subq.a_count");
    }

    public void testNegative() throws Exception {
        Client client = getClient();

        // Currently only fixed-width types are allowed

        verifyStmtFails(client,
                "select approx_count_distinct(vc) from unsupported_column_types;",
                "incompatible data type in operation");

        verifyStmtFails(client,
                "select approx_count_distinct(vb) from unsupported_column_types;",
                "incompatible data type in operation");

        verifyStmtFails(client,
                "select approx_count_distinct(vc_inline) from unsupported_column_types;",
                "incompatible data type in operation");

        verifyStmtFails(client,
                "select approx_count_distinct(vb_inline) from unsupported_column_types;",
                "incompatible data type in operation");
    }

    public TestApproxCountDistinctSuite(String name) {
        super(name);
    }

    static public junit.framework.Test suite() {

        VoltServerConfig config = null;
        MultiConfigSuiteBuilder builder = new MultiConfigSuiteBuilder(TestApproxCountDistinctSuite.class);
        VoltProjectBuilder project = new VoltProjectBuilder();
        final String literalSchema =
                "CREATE TABLE r ( " +
                "pk integer primary key not null, " +
                "bi bigint, " +
                "ii integer, " +
                "si smallint, " +
                "ti tinyint, " +
                "ff float, " +
                "dd decimal, " +
                "ts timestamp " +
                ");" +
                "CREATE TABLE p ( " +
                "pk integer primary key not null, " +
                "bi bigint, " +
                "ii integer, " +
                "si smallint, " +
                "ti tinyint, " +
                "ff float, " +
                "dd decimal, " +
                "ts timestamp " +
                "); " +
                "partition table p on column pk;" +
                "CREATE TABLE unsupported_column_types ( "
                + "vb varbinary(256), "
                + "vc varchar(256),"
                + "vb_inline varbinary(4), "
                + "vc_inline varchar(4), "
                + ");";
        try {
            project.addLiteralSchema(literalSchema);
        } catch (IOException e) {
            assertFalse(true);
        }
        boolean success;

        config = new LocalCluster("testApproxCountDistinctSuite-onesite.jar", 3, 1, 0, BackendTarget.NATIVE_EE_JNI);
        success = config.compile(project);
        assert(success);
        builder.addServerConfig(config);

        config = new LocalCluster("testApproxCountDistinctSuite-onesite.jar", 1, 1, 0, BackendTarget.NATIVE_EE_JNI);
        success = config.compile(project);
        assert(success);
        builder.addServerConfig(config);

        return builder;
    }
}
