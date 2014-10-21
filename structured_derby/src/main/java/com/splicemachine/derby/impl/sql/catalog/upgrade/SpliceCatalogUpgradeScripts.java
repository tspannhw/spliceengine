package com.splicemachine.derby.impl.sql.catalog.upgrade;

import com.splicemachine.derby.impl.sql.catalog.SpliceDataDictionary;
import com.splicemachine.derby.impl.sql.catalog.Splice_DD_Version;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.store.access.TransactionController;

import java.util.*;

/**
 * Created by jyuan on 10/14/14.
 */
public class SpliceCatalogUpgradeScripts {

    SpliceDataDictionary sdd;
    Splice_DD_Version catalogVersion;
    TransactionController tc;
    TreeMap<Splice_DD_Version, UpgradeScript> scripts;
    Comparator<Splice_DD_Version> ddComparator;

    public SpliceCatalogUpgradeScripts (SpliceDataDictionary sdd, Splice_DD_Version catalogVersion, TransactionController tc) {
        this.sdd = sdd;
        this.catalogVersion = catalogVersion;
        this.tc = tc;

        ddComparator = new Comparator<Splice_DD_Version>() {
            @Override
            public int compare(Splice_DD_Version version1, Splice_DD_Version version2) {
                long v1 = version1.toLong();
                long v2 = version2.toLong();

                if (v1 < v2)
                    return -1;
                else if (v1 == v2)
                    return 0;
                else
                    return 1;
            }
        };
        scripts = new TreeMap<Splice_DD_Version, UpgradeScript>(ddComparator);
        scripts.put(new Splice_DD_Version(sdd, 1, 0, 0), new UpgradeScriptForFuji(sdd, tc));
    }

    public void run() throws StandardException {

        sdd.createOrUpdateAllSystemProcedures(tc);
        sdd.updateMetadataSPSes(tc);

        NavigableSet<Splice_DD_Version> keys = scripts.navigableKeySet();
        Iterator<Splice_DD_Version> iterator = keys.iterator();
        while (iterator.hasNext()) {
            Splice_DD_Version version = iterator.next();
            if (catalogVersion != null) {
                if (ddComparator.compare(version, catalogVersion) < 0) {
                    continue;
                }
            }
            UpgradeScript script = scripts.get(version);
            script.run();
        }

    }
}