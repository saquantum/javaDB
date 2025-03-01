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

public class MyDBTests {
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
        return assertTimeoutPreemptively(Duration.ofMillis(5000), () -> {
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

        // update multiple columns of rows.
        assertTrue(sendCommandToServer("UPDATE marks SET city='London',mark=40 WHERE mark<=40;").contains("[OK]"));
        response = sendCommandToServer("select * from marks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*1\\s+Simon\\s+65\\s+TRUE\\s+Bristol.*2\\s+Sion\\s+55\\s+TRUE\\s+Bristol.*3\\s+Rob\\s+40\\s+FALSE\\s+London.*4\\s+Chris\\s+40\\s+FALSE\\s+London.*"));

        // update all rows
        assertTrue(sendCommandToServer("UPDATE marks SET pass = 'true', name='abc', mark = 100, city = 'Tokyo' WHERE id>0;").contains("[OK]"));
        response = sendCommandToServer("select * from marks;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*1\\s+abc\\s+100\\s+true\\s+Tokyo.*2\\s+abc\\s+100\\s+true\\s+Tokyo.*3\\s+abc\\s+100\\s+true\\s+Tokyo.*4\\s+abc\\s+100\\s+true\\s+Tokyo.*"));

        // restore the values
        assertTrue(sendCommandToServer("DROP TABLE marks;").contains("[OK]"));
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, pass);").contains("[OK]"));
        sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Sion',  55, TRUE);");
        sendCommandToServer("INSERT INTO marks VALUES ('Rob',   35, FALSE );");
        sendCommandToServer("INSERT INTO marks VALUES ('Chris', 20, FALSE);");

        // create coursework table
        assertTrue(sendCommandToServer("CREATE TABLE coursework;").contains("[OK]"));
        assertTrue(sendCommandToServer("ALTER TABLE coursework ADD task;").contains("[OK]"));
        assertTrue(sendCommandToServer("ALTER TABLE coursework ADD submission;").contains("[OK]"));
        sendCommandToServer("INSERT INTO coursework VALUES ('OXO', 3);");
        sendCommandToServer("INSERT INTO coursework VALUES ('DB', 1);");
        sendCommandToServer("INSERT INTO coursework VALUES ('OXO', 4);");
        sendCommandToServer("INSERT INTO coursework VALUES ('STAG', 2);");

        // edge case: invalid usage of JOIN
        assertTrue(sendCommandToServer("JOIN coursework and marks on submission and id").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework and marks on submission id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework and marks submission and id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework marks on submission and id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework  on submission and id;").contains("[ERROR]"));

        // edge case: join tables do not exist or attributes do not exist
        assertTrue(sendCommandToServer("JOIN table and marks on submission and id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework and table on submission and id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework and marks on city and id;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("JOIN coursework and marks on submission and city;").contains("[ERROR]"));

        // valid join
        response = sendCommandToServer("JOIN coursework AND marks ON submission AND id;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*id\\s+coursework.task\\s+marks.name\\s+marks.mark\\s+marks.pass\\s+1\\s+OXO\\s+Rob\\s+35\\s+FALSE\\s+" +
                "2\\s+DB\\s+Simon\\s+65\\s+TRUE\\s+3\\s+OXO\\s+Chris\\s+20\\s+FALSE\\s+4\\s+STAG\\s+Sion\\s+55\\s+TRUE.*"));

        response = sendCommandToServer("JOIN marks AND coursework ON id AND submission;").replaceAll("[\\x00-\\x1F\\x7F]", "");
        assertTrue(response.matches(".*id\\s+marks.name\\s+marks.mark\\s+marks.pass\\s+coursework.task\\s+1\\s+Simon\\s+65\\s+TRUE\\s+DB\\s+" +
                "2\\s+Sion\\s+55\\s+TRUE\\s+STAG\\s+3\\s+Rob\\s+35\\s+FALSE\\s+OXO\\s+4\\s+Chris\\s+20\\s+FALSE\\s+OXO.*"));

        // create new tables and drop them.
        assertTrue(sendCommandToServer("CREATE TABLE newTable1;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable1;").contains("[OK]"));
        assertTrue(sendCommandToServer("CREATE TABLE newTable2;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable2;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable1;").contains("[ERROR]"));

        // edge case: invalid usage of DROP
        assertTrue(sendCommandToServer("CREATE TABLE newTable;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DROP newTable;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DROP TABLE new;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("DROP TABLE;").contains("[ERROR]"));

        // edge case: drop table does not exist
        assertTrue(sendCommandToServer("DROP TABLE newTable;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP TABLE newTable;").contains("[ERROR]"));

        assertTrue(sendCommandToServer("DROP DATABASE testBase;").contains("[OK]"));
        assertTrue(sendCommandToServer("USE testbase;").contains("[ERROR]"));
    }

    @Test
    public void testExampleTranscript() {
        String response;

        // Drop the database if it exists
        if (new File("databases/markbook").exists()) {
            assertTrue(sendCommandToServer("DROP DATABASE markbook;").contains("[OK]"));
        }

        // Create and use the database
        assertTrue(sendCommandToServer("CREATE DATABASE markbook;").contains("[OK]"));
        assertTrue(sendCommandToServer("USE markbook;").contains("[OK]"));

        // Create table and insert data
        assertTrue(sendCommandToServer("CREATE TABLE marks (name, mark, pass);").contains("[OK]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('Simon', 65, TRUE);").contains("[OK]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('Sion', 55, TRUE);").contains("[OK]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('Rob', 35, FALSE);").contains("[OK]"));
        assertTrue(sendCommandToServer("INSERT INTO marks VALUES ('Chris', 20, FALSE);").contains("[OK]"));

        // Select all records
        response = sendCommandToServer("SELECT * FROM marks;");
        assertTrue(response.contains("[OK]"));
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertTrue(response.contains("Rob"));
        assertTrue(response.contains("Chris"));

        // Conditional select
        response = sendCommandToServer("SELECT * FROM marks WHERE name != 'Sion';");
        assertTrue(response.contains("[OK]"));
        assertFalse(response.contains("Sion"));

        response = sendCommandToServer("SELECT * FROM marks WHERE pass == TRUE;");
        assertTrue(response.contains("[OK]"));
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Sion"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));

        // Update and delete operations
        assertTrue(sendCommandToServer("UPDATE marks SET mark = 38 WHERE name == 'Chris';").contains("[OK]"));
        response = sendCommandToServer("SELECT * FROM marks WHERE name == 'Chris';");
        assertTrue(response.contains("38"));

        assertTrue(sendCommandToServer("DELETE FROM marks WHERE name == 'Sion';").contains("[OK]"));
        response = sendCommandToServer("SELECT * FROM marks;");
        assertFalse(response.contains("Sion"));

        // Select with multiple conditions
        response = sendCommandToServer("SELECT * FROM marks WHERE (pass == FALSE) AND (mark > 35);");
        assertTrue(response.contains("Chris"));
        assertFalse(response.contains("Simon"));
        assertFalse(response.contains("Rob"));

        // Using LIKE in SELECT
        response = sendCommandToServer("SELECT * FROM marks WHERE name LIKE 'i';");
        assertTrue(response.contains("Simon"));
        assertTrue(response.contains("Chris"));
        assertFalse(response.contains("Sion"));
        assertFalse(response.contains("Rob"));

        // Additional SELECT queries
        response = sendCommandToServer("SELECT id FROM marks WHERE pass == FALSE;");
        assertTrue(response.contains("3"));
        assertTrue(response.contains("4"));

        response = sendCommandToServer("SELECT name FROM marks WHERE mark>60;");
        assertTrue(response.contains("Simon"));

        assertTrue(sendCommandToServer("DELETE FROM marks WHERE mark<40;").contains("[OK]"));
        response = sendCommandToServer("SELECT * FROM marks;");
        assertTrue(response.contains("Simon"));
        assertFalse(response.contains("Rob"));
        assertFalse(response.contains("Chris"));

        // Altering table structure
        assertTrue(sendCommandToServer("ALTER TABLE marks ADD age;").contains("[OK]"));
        assertTrue(sendCommandToServer("UPDATE marks SET age = 35 WHERE name == 'Simon';").contains("[OK]"));
        response = sendCommandToServer("SELECT * FROM marks;");
        assertTrue(response.contains("35"));

        assertTrue(sendCommandToServer("ALTER TABLE marks DROP pass;").contains("[OK]"));
        response = sendCommandToServer("SELECT * FROM marks;");
        assertFalse(response.contains("pass"));

        // Handling errors
        assertTrue(sendCommandToServer("SELECT * FROM marks").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT * FROM crew;").contains("[ERROR]"));
        assertTrue(sendCommandToServer("SELECT height FROM marks WHERE name == 'Chris';").contains("[ERROR]"));

        // Dropping tables and database
        assertTrue(sendCommandToServer("DROP TABLE marks;").contains("[OK]"));
        assertTrue(sendCommandToServer("DROP DATABASE markbook;").contains("[OK]"));
    }

}
