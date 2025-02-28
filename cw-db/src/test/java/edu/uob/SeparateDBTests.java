package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class SeparateDBTests {
    private DBServer server;

    @BeforeEach
    public void setup() {
        server = new DBServer();
    }

    // Random name generator - useful for testing "bare earth" queries (i.e. where tables don't previously exist)
    private String generateRandomName() {
        String randomName = "";
        for (int i = 0; i < 10; i++) randomName += (char) (97 + (Math.random() * 25.0));
        return randomName;
    }

    private String sendCommandToServer(String command) {
        // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
        return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> {
                    return server.handleCommand(command);
                },
                "Server took too long to respond (probably stuck in an infinite loop)");
    }

    @Test
    public void testQuery() throws InterruptedException {
        String response;
        // clear testBase if it exists.
        if (new File("databases/testbase").exists()) {
            assertTrue(sendCommandToServer("DROP DATABASE testBase;").contains("[OK]"));
        } else {
            assertTrue(sendCommandToServer("DROP DATABASE testBase;").contains("[ERROR]"));
        }

        // edge case: invalid usage of USE.
        assertTrue(sendCommandToServer("USE").contains("[ERROR]"));
        assertTrue(sendCommandToServer("USE;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("USE DATABASE testBase;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("USE base DATABASE;").contains("[ERROR]"));

        // edge case: use database does not exist.
        assertTrue(sendCommandToServer("USE testBase;").contains("[ERROR]"));

        // create testBase, but does not use it, then create table.
        assertTrue(sendCommandToServer("CREATE DATABASE testBase;").contains("[OK]"));
        assertTrue(sendCommandToServer("CREATE TABLE testTable;").contains("[ERROR]"));

        // edge case: create duplicate database.
        assertTrue(sendCommandToServer("CREATE DATABASE   testBase;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("  CREATE   DATABASE testBASE;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("   CREATE DATABASE testbase  ;   ").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE DATABASE    TestBase;").contains("[ERROR]"));

        // switch to testBase, create table
        assertTrue(sendCommandToServer("USE TESTBase;").contains("[OK]"));

        // edge case: assign ID attribute manually.
        assertTrue(sendCommandToServer("CREATE TABLE marks (Id, name, mark, pass);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, ID);").contains("[ERROR]"));

        // edge case: assign duplicate attributes.
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, NamE);").contains("[ERROR]"));

        // edge case: invalid usage of CREATE.
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, pass)").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, pass;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks name, mark, pass;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, , pass);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, );").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (, mark, pass);").contains("[ERROR]"));

        // create and insert.
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, pass);").contains("[OK]")); // break point here
        sendCommandToServer("INSERT INTO marks VALUES ('Simon' ,   65,    TRUE)   ;  ");
        sendCommandToServer("INSERT INTO marks VALUES (  'Sion'  , 55, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ( 'Rob', 35, FALSE );");
        sendCommandToServer("INSERT INTO marks VALUES ('Chris',20,FALSE);");

        // edge case: create duplicate tables.
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE MarKs ;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (age, ciTy, nation);").contains("[ERROR]"));

        // edge case: invalid usage of INSERT.
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('A', 1, TRUE)").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('A', 1, TRUE;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES 'A', 1, TRUE;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES 'A', 1, TRUE);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES 'A', 1, TRUE;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('A', 1, TRUE, );").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES (, 'A', 1, TRUE);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT INTO marks ('A', 1, TRUE);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("INSERT marks VALUES ('A', 1, TRUE);").contains("[ERROR]"));

        // edge case: select attributes do not exist.
        assertTrue(sendCommandToServer("SELECT course from marks;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT name1 from marks;").contains("[ERROR]"));

        // edge case: invalid usage of SELECT.
        assertTrue(sendCommandToServer("SELECT mark from marks").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT mark,  from marks;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT   from marks;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT ,mark, name  from marks;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT mark, name  marks;").contains("[ERROR]"));

        // select valid cases
        response = sendCommandToServer("SELECT * FROM marks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*\\[OK].*1\\s+Simon\\s+65\\s+TRUE.*2\\s+Sion\\s+55\\s+TRUE.*3\\s+Rob\\s+35\\s+FALSE.*4\\s+Chris\\s+20\\s+FALSE.*"));
        response = sendCommandToServer("SELECT mark, name FROM marks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*\\[OK].*id\\s+name\\s+mark\\s+pass\\s+65\\s+Simon\\s+55\\s+Sion\\s+35\\s+Rob\\s+20\\s+Chris.*"));
        response = sendCommandToServer("SELECT name, name, id FROM marks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*\\[OK].*id\\s+name\\s+mark\\s+pass\\s+Simon\\s+Simon\\s+1\\s+Sion\\s+Sion\\s+2\\s+Rob\\s+Rob\\s+3\\s+Chris\\s+Chris 4.*"));
        response = sendCommandToServer("SELECT Name, NAME, iD FROM maRks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*\\[OK].*id\\s+name\\s+mark\\s+pass\\s+Simon\\s+Simon\\s+1\\s+Sion\\s+Sion\\s+2\\s+Rob\\s+Rob\\s+3\\s+Chris\\s+Chris 4.*"));

        // select with condition
        response = sendCommandToServer("SELECT * FROM marks WHERE id>2;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertTrue(response.contains("Rob"));
        assertTrue(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE id<3;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE id<4 AND id>1;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertFalse(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertTrue(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name like 'S%';").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name like 'S%' and id>1;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertFalse(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name like 'S%' or id>3;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertTrue(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE (id < 3 OR name LIKE 'C%') AND pass == TRUE;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE id > 2 AND name LIKE 'S%' OR pass == FALSE ;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertTrue(response.contains("Rob"));
        assertTrue(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE (id > 1 AND pass == TRUE) OR name == 'Chris';").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertFalse(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertTrue(response.contains("Chris"));
        assertFalse(response.contains("Rob"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name like 'S%' and mark>=60 or id==2 or pass=='false' and name like 'R%' or mark <=30 ;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertTrue(response.contains("Chris"));
        assertTrue(response.contains("Rob"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name like 'S%' and ((mark>=60 or id==2) or pass=='false') and (name like 'R%' or mark >=30) ;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Chris"));
        assertFalse(response.contains("Rob"));

        // invalid condition
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE id<4 AND;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE id<4 AND id > ;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE  AND id<4;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE AND;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE id<4 id>1;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE <4 or id>1;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE id<4 and id>1 name!='Sion';").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE (id<4 and id>1) and ;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE (id<4 and id>1) and ();").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE (id<4 and id>1) and (id<4 or id);").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM marks WHERE ((id<4 and id>1) and (id<4 or id));").contains("[ERROR]"));

        // these condition that var type does not match select no rows
        response = sendCommandToServer("SELECT * FROM marks WHERE id like 'a';");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE mark like 'a0';");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name > 0;");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));

        // with useless condition, the select still works.
        response = sendCommandToServer("SELECT * FROM marks WHERE name > 0 or id < 2;");
        assertTrue(response.contains("[OK]"));
        assertTrue(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));

        // edge case: invalid usage of DELETE.
        assertTrue(sendCommandToServer("DELETE FROM marks id < 2;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DELETE FROM marks WHERE id 2;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DELETE marks WHERE id < 2;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DELETE FROM marks ;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DELETE FROM marks WHERE ;").contains("[ERROR]"));

        // delete the rows from table
        response = sendCommandToServer("DELETE FROM marks WHERE id == 1;");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Simon"));
        response = sendCommandToServer("DELETE FROM marks WHERE name like 'S%';");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Sion"));
        response = sendCommandToServer("DELETE FROM marks WHERE mark >= 10;");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));

        // can continue to delete even if the table is empty
        response = sendCommandToServer("DELETE FROM marks WHERE mark >= 10;");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));

        // restore the values
        assertTrue(sendCommandToServer("DROP TABLE marks;").contains("[OK]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, pass);").contains("[OK]"));
        sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Sion',  55, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Rob',   35, FALSE );");
        sendCommandToServer("INSERT INTO marks VALUES ('Chris', 20, FALSE);");

        // edge case: invalid usage of ALTER
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD abc").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER TABLE marks DROP id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD ;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD name;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD NaMe;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER database marks ADD abc;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("ALTER marks ADD abc;").contains("[ERROR]"));

        // alter table does not exist
        assertTrue(sendCommandToServer("ALTER TABLE table ADD abc;").contains("[ERROR]"));

        // drop attribute does not exist
        assertTrue(sendCommandToServer("ALTER TABLE marks DROP abc;").contains("[ERROR]"));

        // add a column and drop it.
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD abc;").contains("[OK]"));
        assertTrue(sendCommandToServer("ALTER TABLE marks DROP abc;").contains("[OK]"));

        // add a column, and update rows.
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD city;").contains("[OK]"));
        assertTrue(sendCommandToServer("UPDATE marks SET city='Bristol' WHERE id==1 or name like 'S___' ;").contains("[OK]"));
        response = sendCommandToServer("select * from marks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*1\\s+Simon\\s+65\\s+TRUE\\s+Bristol.*2\\s+Sion\\s+55\\s+TRUE\\s+Bristol.*3\\s+Rob\\s+35\\s+FALSE\\s+NULL.*4\\s+Chris\\s+20\\s+FALSE\\s+NULL.*"));

        // create new tables and drop them.
        assertTrue(sendCommandToServer("CREATE TABLE newTable1;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable1;").contains("[OK]"));
        assertTrue(sendCommandToServer("CREATE TABLE newTable2;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable2;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable1;").contains("[ERROR]"));

        assertTrue(sendCommandToServer("CREATE TABLE newTable;").contains("[OK]"));

        // edge case: invalid usage of DROP
        assertTrue(sendCommandToServer("DROP TABLE newTable").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DROP newTable;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DROP TABLE new;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DROP TABLE;").contains("[ERROR]"));

        // edge case: drop table does not exist
        assertTrue(sendCommandToServer("DROP TABLE newTable1;").contains("[ERROR]"));

        assertTrue(sendCommandToServer("DROP DATABASE testBase;").contains("[OK]"));
        assertTrue(sendCommandToServer("USE testbase;").contains("[ERROR]"));
    }
}
