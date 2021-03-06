package seeding;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/21/16.
 */
public class SeedValuablePatentsData {
    public SeedValuablePatentsData() throws Exception {
        Database.resetValuablePatents();
        Database.commit();
        AtomicInteger cnt = new AtomicInteger(0);
        for(String patent : Database.getValuablePatentsToList()) {
            try {
                Database.updateValuablePatents(patent,true);
                System.out.println(cnt.getAndIncrement());

            } catch (SQLException sql) {
                sql.printStackTrace();
            }
        }
        Database.commit();
    }

    public static void main(String[] args) {
        try {
            Database.setupMainConn();
            Database.setupSeedConn();
            new SeedValuablePatentsData();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }

}
