package liquibase.changelog;

import liquibase.Contexts;
import liquibase.configuration.LiquibaseConfiguration;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import liquibase.database.core.H2Database;


public class ChangeLogParametersTest {

    private LiquibaseConfiguration liquibaseConfiguration;

    @Before
    public void before() {
        liquibaseConfiguration = new LiquibaseConfiguration();
    }

    @Test
    public void setParameterValue_doubleSet() {
        ChangeLogParameters changeLogParameters = new ChangeLogParameters(liquibaseConfiguration);

        changeLogParameters.set("doubleSet", "originalValue");
        changeLogParameters.set("doubleSet", "newValue");

        assertEquals("re-setting a param should not overwrite the value (like how ant works)", "originalValue", changeLogParameters.getValue("doubleSet"));
    }

    @Test
    public void getParameterValue_systemProperty() {
        ChangeLogParameters changeLogParameters = new ChangeLogParameters(liquibaseConfiguration);

        assertEquals(System.getProperty("user.name"), changeLogParameters.getValue("user.name"));
    }

    @Test
    public void setParameterValue_doubleSetButSecondWrongDatabase() {
        ChangeLogParameters changeLogParameters = new ChangeLogParameters(new H2Database(), liquibaseConfiguration);

        changeLogParameters.set("doubleSet", "originalValue", new Contexts(), "baddb");
        changeLogParameters.set("doubleSet", "newValue");

        assertEquals("newValue", changeLogParameters.getValue("doubleSet"));
    }

    @Test
    public void setParameterValue_multiDatabase() {
        ChangeLogParameters changeLogParameters = new ChangeLogParameters(new H2Database(), liquibaseConfiguration);

        changeLogParameters.set("doubleSet", "originalValue", new Contexts(), "baddb, h2");

        assertEquals("originalValue", changeLogParameters.getValue("doubleSet"));
    }

    @Test
    public void setParameterValue_rightDBWrongContext() {
        ChangeLogParameters changeLogParameters = new ChangeLogParameters(new H2Database(), liquibaseConfiguration);
        changeLogParameters.setContexts(new Contexts("junit"));

        changeLogParameters.set("doubleSet", "originalValue", "anotherContext", "baddb, h2");

        assertNull(changeLogParameters.getValue("doubleSet"));
    }
   @Test
    public void setParameterValue_rightDBRightContext() {
        ChangeLogParameters changeLogParameters = new ChangeLogParameters(new H2Database(), liquibaseConfiguration);
        changeLogParameters.setContexts(new Contexts("junit"));

        changeLogParameters.set("doubleSet", "originalValue", "junit", "baddb, h2");

        assertEquals("originalValue", changeLogParameters.getValue("doubleSet"));
    }
}