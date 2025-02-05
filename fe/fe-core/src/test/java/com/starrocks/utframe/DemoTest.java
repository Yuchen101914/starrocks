// This file is made available under Elastic License 2.0.
// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/test/java/org/apache/doris/utframe/DemoTest.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.utframe;

import com.starrocks.alter.AlterJobV2;
import com.starrocks.analysis.AlterTableStmt;
import com.starrocks.catalog.Catalog;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.MaterializedIndexMeta;
import com.starrocks.catalog.OlapTable;
import com.starrocks.common.DdlException;
import com.starrocks.common.FeConstants;
import com.starrocks.common.util.UUIDUtil;
import com.starrocks.planner.OlapScanNode;
import com.starrocks.planner.PlanFragment;
import com.starrocks.planner.Planner;
import com.starrocks.qe.ConnectContext;
import com.starrocks.qe.StmtExecutor;
import com.starrocks.utframe.MockedFrontend.EnvVarNotSetException;
import com.starrocks.utframe.MockedFrontend.FeStartException;
import com.starrocks.utframe.MockedFrontend.NotInitException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * This demo shows how to run unit test with mocked FE and BE.
 * It will
 *  1. start a mocked FE and a mocked BE.
 *  2. Create a database and a tbl.
 *  3. Make a schema change to tbl.
 *  4. send a query and get query plan
 */
public class DemoTest {

    // use a unique dir so that it won't be conflict with other unit test which
    // may also start a Mocked Frontend
    private static String runningDirBase = "fe";
    private static String runningDir = runningDirBase + "/mocked/DemoTest/" + UUID.randomUUID().toString() + "/";
    private static StarRocksAssert starRocksAssert;

    @BeforeClass
    public static void beforeClass() throws EnvVarNotSetException, IOException,
            FeStartException, NotInitException, DdlException, InterruptedException {
        FeConstants.default_scheduler_interval_millisecond = 10;
        UtFrameUtils.createMinStarRocksCluster(runningDir);
    }

    @AfterClass
    public static void TearDown() {
        UtFrameUtils.cleanStarRocksFeDir(runningDirBase);
    }

    @Test
    public void testCreateDbAndTable() throws Exception {
        // 1. create connect context
        ConnectContext ctx = UtFrameUtils.createDefaultCtx();
        ctx.setQueryId(UUIDUtil.genUUID());
        starRocksAssert = new StarRocksAssert(ctx);
        // 2. create database db1
        starRocksAssert.withDatabase("db1").useDatabase("db1");
        // 3. create table tbl1
        starRocksAssert.withTable(
                "create table db1.tbl1(k1 int) distributed by hash(k1) buckets 3 properties('replication_num' = '1');");
        // 4. get and test the created db and table
        Database db = Catalog.getCurrentCatalog().getDb("default_cluster:db1");
        Assert.assertNotNull(db);
        db.readLock();
        try {
            OlapTable tbl = (OlapTable) db.getTable("tbl1");
            Assert.assertNotNull(tbl);
            System.out.println(tbl.getName());
            Assert.assertEquals("StarRocks", tbl.getEngine());
            Assert.assertEquals(1, tbl.getBaseSchema().size());
        } finally {
            db.readUnlock();
        }
        // 5. process a schema change job
        String alterStmtStr = "alter table db1.tbl1 add column k2 int default '1'";
        AlterTableStmt alterTableStmt = (AlterTableStmt) UtFrameUtils.parseAndAnalyzeStmt(alterStmtStr, ctx);
        Catalog.getCurrentCatalog().getAlterInstance().processAlterTable(alterTableStmt);
        // 6. check alter job
        Map<Long, AlterJobV2> alterJobs = Catalog.getCurrentCatalog().getSchemaChangeHandler().getAlterJobsV2();
        Assert.assertEquals(1, alterJobs.size());
        for (AlterJobV2 alterJobV2 : alterJobs.values()) {
            while (!alterJobV2.getJobState().isFinalState()) {
                System.out.println(
                        "alter job " + alterJobV2.getJobId() + " is running. state: " + alterJobV2.getJobState());
                Thread.sleep(1000);
            }
            System.out.println("alter job " + alterJobV2.getJobId() + " is done. state: " + alterJobV2.getJobState());
            Assert.assertEquals(AlterJobV2.JobState.FINISHED, alterJobV2.getJobState());
        }
        db.readLock();
        try {
            OlapTable tbl = (OlapTable) db.getTable("tbl1");
            Assert.assertEquals(2, tbl.getBaseSchema().size());

            String baseIndexName = tbl.getIndexNameById(tbl.getBaseIndexId());
            Assert.assertEquals(baseIndexName, tbl.getName());
            MaterializedIndexMeta indexMeta = tbl.getIndexMetaByIndexId(tbl.getBaseIndexId());
            Assert.assertNotNull(indexMeta);
        } finally {
            db.readUnlock();
        }
        // 7. query
        // TODO: we can not process real query for now. So it has to be a explain query
        String queryStr = "explain select * from db1.tbl1";
        StmtExecutor stmtExecutor = new StmtExecutor(ctx, queryStr);
        stmtExecutor.execute();
        Planner planner = stmtExecutor.planner();
        List<PlanFragment> fragments = planner.getFragments();
        Assert.assertEquals(1, fragments.size());
        PlanFragment fragment = fragments.get(0);
        Assert.assertTrue(fragment.getPlanRoot() instanceof OlapScanNode);
        Assert.assertEquals(0, fragment.getChildren().size());
    }
}
