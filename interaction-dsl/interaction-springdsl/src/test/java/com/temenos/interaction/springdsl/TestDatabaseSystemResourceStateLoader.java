package com.temenos.interaction.springdsl;

/*
 * #%L
 * interaction-springdsl
 * %%
 * Copyright (C) 2012 - 2018 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;

import com.temenos.interaction.core.hypermedia.Event;
import com.temenos.interaction.core.hypermedia.MethodNotAllowedException;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateProvider;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.ConfigLoader;
import com.temenos.interaction.metadata.resource.MetadataResourceProvider;

/**
 * Unit test for {@link DatabaseSystemResourceStateLoader}
 *
 * @author mohamednazir
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class TestDatabaseSystemResourceStateLoader {
    
    static ApplicationContext ctx;
    protected ResourceStateProvider resourceStateProvider;
    static DatabaseSystemResourceStateLoader loader = new DatabaseSystemResourceStateLoader();
    MetadataResourceProvider metadataResourceProvider = Mockito.mock(MetadataResourceProvider.class);
    static InputStream inputStream = null;
    static ConfigLoader configLoader = new ConfigLoader();
        
    @BeforeClass
    public static void setUpClass() {
        configLoader.setIrisConfigDirPath("");
        loader.setConfigLoader(configLoader);
    }
    
    @Before
    public void setup(){
        inputStream = TestDatabaseSystemResourceStateLoader.class.getResourceAsStream("/com/temenos/interaction/springdsl/TestSpringDSLResourceStateProvider-context.xml");
        ctx = loader.createApplicationContext(inputStream);
        resourceStateProvider = (ResourceStateProvider) ctx.getBean("resourceStateProvider");
        
    }

    @Test
    public void testCreateApplicationContext() throws Exception {
        String fileName ="/IRIS-SimpleModel_Home_home-PRD.xml";
        InputStream testIs= TestDatabaseSystemResourceStateLoader.class.getResourceAsStream(fileName);
        assertNotNull(testIs);
        Mockito.when( metadataResourceProvider.readFile(Mockito.anyString())).thenReturn(testIs);
        loader.setMetadataResourceProvider(metadataResourceProvider);
        ApplicationContext testCtx = loader.createApplicationContext(fileName);
        assertNotNull(testCtx);
    }
    
    @Test
    public void testNullApplicationContext() throws Exception {
        String fileName ="/IRIS-SimpleModel_Home_home-PRD.xml";
        Mockito.when( metadataResourceProvider.readFile(Mockito.anyString())).thenReturn(null);
        loader.setMetadataResourceProvider(metadataResourceProvider);
        assertNull(loader.createApplicationContext(fileName));
    }
    
    
    @Test
    public void testGetResourceState() {
        ResourceState actual = resourceStateProvider.getResourceState("SimpleModel_Home_home");
        assertEquals("home", actual.getName());
    }

    @Test
    public void testGetResourceStateWithTransitionsInitialised() {
        ResourceState actual = resourceStateProvider.getResourceState("SimpleModel_Home_TestTransition");
        assertEquals("TestTransition", actual.getName());
        List<Transition> transitions = actual.getTransitions();
        assertEquals(1, transitions.size());
        assertEquals("access a property from the target to check it is lazy loaded", "LAZY", transitions.get(0).getTarget().getPath());
    }
    
    @Test
    public void testGetResourceStateMultipleStatesPerFile() {
        ResourceState actual = resourceStateProvider.getResourceState("SimpleModel_Home-home");
        assertEquals("home", actual.getName());
    }
    

    @Test
    public void testGetResourceStatesByPath() {
        Properties properties = new Properties();
        ResourceLoader resourceLoader = new DatabaseSystemResourceStateLoader();
        properties.put("SimpleModel_Home_home", "GET /test");
        ResourceStateProvider rsp = new SpringDSLResourceStateProvider(properties,resourceLoader);
        Map<String, Set<String>> statesByPath = rsp.getResourceStatesByPath();
        assertEquals(1, statesByPath.size());
        assertEquals(1, statesByPath.get("/test").size());
        assertEquals("SimpleModel_Home_home", statesByPath.get("/test").toArray()[0]);
    }
    
    @Test
    public void testGetResourceMethodsByState() {
        Map<String, Set<String>> methods = resourceStateProvider.getResourceMethodsByState();
        assertNotNull(methods);
        assertEquals("Methods for state SimpleModel_Home_home", 2, methods.get("SimpleModel_Home_home").size());
    }

    @Test
    public void testGetResourceStateByRequest() {
        // properties: SimpleModel_Home_home=GET,PUT /test
        ResourceState foundGetState = resourceStateProvider.determineState(new Event("GET", "GET"), "/test");
        assertEquals("home", foundGetState.getName());
        ResourceState foundPutState = resourceStateProvider.determineState(new Event("PUT", "PUT"), "/test");
        assertEquals("home", foundPutState.getName());
    }

    @Test
    public void testGetResourceStateByMethodUrl() throws MethodNotAllowedException {
        // properties: SimpleModel_Home_home=GET,PUT /test
        ResourceState foundGetState = resourceStateProvider.getResourceState("GET", "/test");
        assertEquals("home", foundGetState.getName());
        ResourceState foundPutState = resourceStateProvider.getResourceState("PUT", "/test");
        assertEquals("home", foundPutState.getName());
    }

    @Test
    public void testGetResourceStateId() throws MethodNotAllowedException {
        String actual = resourceStateProvider.getResourceStateId("GET", "/test");
        assertEquals("SimpleModel_Home_home", actual);
        // as opposed to "home", which is the resource state name
        ResourceState foundGetState = resourceStateProvider.getResourceState("GET", "/test");
        assertThat("SimpleModel_Home_home", not(foundGetState.getName()));
    }
    
    @Test
    public void testIsLoaded() throws Exception {
        
        Properties properties = new Properties();
        ResourceLoader resourceLoader = new DatabaseSystemResourceStateLoader();
        String filenamePRD = "/IRIS-SimpleModel_Home_home-PRD.xml";
        
        InputStream testIsPRD= TestDatabaseSystemResourceStateLoader.class.getResourceAsStream(filenamePRD);
        List<InputStream> inStreamList = new ArrayList<InputStream>();
        inStreamList.add(testIsPRD);
        
        assertNotNull(inStreamList);
         Mockito.when( metadataResourceProvider.readListOfFiles(Mockito.anyString())).thenReturn(inStreamList);
        ((DatabaseSystemResourceStateLoader) resourceLoader).setMetadataResourceProvider(metadataResourceProvider);
        
        
        properties.put("SimpleModel_Home_home", "GET /test");
        ResourceStateProvider rsp = new SpringDSLResourceStateProvider(properties,resourceLoader);
        
        assertFalse(rsp.isLoaded("SimpleModel_Home_home"));
        assertFalse(rsp.isLoaded("inexistentState"));

        // this is the current way of loading resources...
        rsp.getResourceState("SimpleModel_Home_home");

        assertTrue(rsp.isLoaded("SimpleModel_Home_home"));
        assertFalse(rsp.isLoaded("inexistentState"));
    }

    @Test
    public void testIsLoadedMultipleFile() throws Exception {
        
        Properties properties = new Properties();
        ResourceLoader resourceLoader = new DatabaseSystemResourceStateLoader();
        String filenamePRD1 = "/PRDFiles/IRIS-Tst_Twins_1494662578833-PRD.xml";
        InputStream testIsPRD1= TestDatabaseSystemResourceStateLoader.class.getResourceAsStream(filenamePRD1);
        String filenamePRD2 = "/PRDFiles/IRIS-Tst_Twins_Foldername_10-PRD.xml";
        InputStream testIsPRD2= TestDatabaseSystemResourceStateLoader.class.getResourceAsStream(filenamePRD2);
        
        List<InputStream> inStreamList = new ArrayList<InputStream>();
        inStreamList.add(testIsPRD1);
        inStreamList.add(testIsPRD2);
        
        assertNotNull(inStreamList);
         Mockito.when( metadataResourceProvider.readListOfFiles(Mockito.anyString())).thenReturn(inStreamList);
        ((DatabaseSystemResourceStateLoader) resourceLoader).setMetadataResourceProvider(metadataResourceProvider);
        
        
        properties.put("SimpleModel_Home_home", "GET /test");
        ResourceStateProvider rsp = new SpringDSLResourceStateProvider(properties,resourceLoader);
        
        assertFalse(rsp.isLoaded("SimpleModel_Home_home"));
        assertFalse(rsp.isLoaded("inexistentState"));

        // this is the current way of loading resources...
        rsp.getResourceState("Tst_Twins-notes");

        assertTrue(rsp.isLoaded("Tst_Twins-notes"));
        assertFalse(rsp.isLoaded("inexistentState"));
    }


    
    @Test
    public void testLoadResourceStateFromDatabase() throws Exception {
        
        String url ="/simple";
        Properties properties = new Properties();
        ResourceStateProvider rsp = new SpringDSLResourceStateProvider(properties,loader);
        String fileName ="/IRIS-Common_simple.properties";
        String mockPRDFile = "IRIS-SimpleModel_Home_home-PRD.xml";
        InputStream testIs= TestDatabaseSystemResourceStateLoader.class.getResourceAsStream(fileName);
        assertNotNull(testIs);
        Mockito.when( metadataResourceProvider.loadResourceStateFromDatabase(url)).thenReturn(testIs);
        
        InputStream is= TestDatabaseSystemResourceStateLoader.class.getResourceAsStream("/"+mockPRDFile);
        assertNotNull(is);
        List<InputStream> inStreamList = new ArrayList<InputStream>();
        inStreamList.add(is);
        Mockito.when( metadataResourceProvider.readListOfFiles(mockPRDFile)).thenReturn(inStreamList);
        loader.setMetadataResourceProvider(metadataResourceProvider);
        ResourceState actual = rsp.getResourceState("GET", url);
      
        assertTrue(rsp.isLoaded("SimpleModel_Home_home"));
        assertEquals("HOME.home", actual.toString());
    }
    

}
