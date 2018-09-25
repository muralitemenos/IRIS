package com.temenos.interaction.springdsl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.temenos.interaction.core.hypermedia.MethodNotAllowedException;
import com.temenos.interaction.core.hypermedia.PathTree;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.AbstractConfigLoaders;
import com.temenos.interaction.core.resource.ConfigLoader;

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

public class FileSystemResourceStateLoader extends SpringDSLResourceStateProvider implements ResourceLoader {

    private final Logger logger = LoggerFactory.getLogger(FileSystemResourceStateLoader.class);

    private ResourceState result;
    private String state;
    private List<String> attempts = new ArrayList<String>(2);
    private String foundFile;
    protected ConcurrentMap<String, ResourceState> resources;

    private AbstractConfigLoaders configLoader;

    @Override
    public void initialise(Properties beanMap, ConcurrentMap<String, ResourceState> resources, ResourceState result, PathTree pathTree) {
        this.beanMap = beanMap;
        this.resources = resources;
        this.result = result;
        this.pathTree = pathTree;
    }

    public String getResourceStateId(String httpMethod, String url) throws MethodNotAllowedException {
        Map<String, String> methodToState = null;

        initialise();
        methodToState = pathTree.get(url);

        String resourceStateId = null;

        if (methodToState != null) {
            resourceStateId = methodToState.get(httpMethod);
            if (resourceStateId == null) {
                if (pathTree.get(url) != null) {
                    Set<String> allowedMethods = pathTree.get(url).keySet();
                    throw new MethodNotAllowedException(allowedMethods);
                }
            }
        } else {
            return null;
        }

        return resourceStateId;
    }

    /**
     * Checks if the provided xml file name is available in class-path else
     * scans for time-stamp included files
     * 
     * @param resourceName
     * @return array of class-path file names
     */
    private String[] getClassPathContextFileNames(String resourceName) {
        List<String> resources = new ArrayList<String>();
        if (this.getClass().getClassLoader().getResource(resourceName) != null) {
            resources.add(resourceName);
        } else {
            String resource = resourceName.replace("-PRD.xml", "");
            /*
             * PathMatchingResourcePatternResolver fails to get resources
             * present in root directory. Hence the relevant properties file
             * inside 'META-INF' is looked-up to conclude that the relevant
             * 'PRD.xml' resource is present in class-path
             */
            String resourceProperty = resource.concat("*.properties");
            Resource[] patternResources;
            try {
                patternResources = new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:META-INF/" + resourceProperty);
                if (patternResources != null) {
                    for (Resource patternResource : patternResources) {
                        if (Pattern.matches(resource.concat(".*_(\\d+).properties"), patternResource.getFilename())) {
                            resources.add(patternResource.getFilename().replace(".properties", "-PRD.xml"));
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Unable to find the resource from classpath");
            }
        }
        return resources.toArray(new String[resources.size()]);
    }

    protected ApplicationContext createApplicationContext(File file) {

        URL fileURL = resolveFileURL(file);
        if (fileURL == null) {
            return null;
        }
        attempts.add(fileURL.toString());
        if (file.exists()) {
            foundFile = fileURL.toString();
            return new FileSystemXmlApplicationContext(new String[] { fileURL.toString() });
        }
        return null;
    }

    private URL resolveFileURL(File file) {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            logger.error("Failed to resolve URL for file: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private ResourceState loadAllResourceStatesFromFile(ApplicationContext context, String resourceState) {
        Map<String, ResourceState> tmpResources = context.getBeansOfType(ResourceState.class);

        // Save all the loaded resources into the main resource state cache
        resources.putAll(tmpResources);

        ResourceState result = null;

        if (tmpResources.containsKey(resourceState)) {
            result = tmpResources.get(resourceState);
        }

        return result;
    }

    private ResourceState loadAllResourceStatesFromTimeStampedResourceState(String resourceState,
            List<String> timestampledFiles) {
        if (timestampledFiles.size() == 0) {
            return null;
        }
        ApplicationContext beanContext = createApplicationContext(timestampledFiles.get(0));
        Map<String, ResourceState> fileResources = beanContext.getBeansOfType(ResourceState.class);
        ResourceState resource = loadAllResourceStatesFromTimeStampedResourceState(resourceState,
                timestampledFiles.subList(1, timestampledFiles.size()));
        if (resource == null) {
            resource = fileResources.get(resourceState);
        } else {
            Set<Transition> newTransitions = new HashSet<>(fileResources.get(resourceState).getTransitions());
            for (Transition transition : newTransitions) {
                transition.setSource(resource);
            }
            newTransitions.addAll(new HashSet<>(resource.getTransitions()));
            resource.getTransitions().clear();
            resource.setTransitions(new ArrayList<Transition>(newTransitions));
        }
        resources.putAll(fileResources);
        resources.put(resourceState, resource);
        return resource;

    }

    private List<String> getTimestampedResourceStateFileLists(String tmpResourceName) {
        List<String> filename = new ArrayList<String>();
        if (configLoader != null) {
            for (String pathToDirectory : ((ConfigLoader) configLoader).getIrisConfigDirPaths()) {

                Path dir = FileSystems.getDefault().getPath(pathToDirectory);
                final PathMatcher matcher = dir.getFileSystem()
                        .getPathMatcher("regex:" + "IRIS-" + tmpResourceName + ".*_(\\d+)-PRD.xml");
                DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>() {

                    @Override
                    public boolean accept(Path entry) {
                        return matcher.matches(entry.getFileName());
                    }
                };
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {

                    for (Path streamEntry : stream) {
                        filename.add(streamEntry.toFile().getName());
                    }
                } catch (IOException e) {
                    logger.error("Failed to load timestamped file from" + dir);
                }
            }
        }
        return filename;
    }

    /**
     * @param beanXml
     *            the filename to locate
     * @return a Spring ApplicationContext
     */
    public ApplicationContext createApplicationContext(String beanXml) {
        ApplicationContext result = null;

        if (configLoader == null || ((ConfigLoader) configLoader).getIrisConfigDirPaths().isEmpty()) {
            // Try and load the resource from the classpath
            String description = "classpath:" + beanXml;
            attempts.add(description);
            String[] contextFiles = getClassPathContextFileNames(beanXml);
            if (contextFiles.length > 0) {
                result = new ClassPathXmlApplicationContext(contextFiles);
            }
            foundFile = description;
        } else {
            // Try and load the resource from the file system as a resource
            // directories has been specified
            for (String directoryPath : ((ConfigLoader) configLoader).getIrisConfigDirPaths()) {
                Path filePath = Paths.get(directoryPath, beanXml);
                result = createApplicationContext(new File(filePath.toString()));
                if (result != null) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Was the load operation successful?
     */
    public boolean isLoaded() {
        return (result != null);
    }

    /**
     * Get the Resource State from a successful load
     */
    public ResourceState loaded() {
        return result;
    }

    /**
     * Description of the state of this load, intended for logging
     */
    public String toString() {
        if (isLoaded()) {
            return "Loaded Resource State " + state + " from " + foundFile;
        } else if (attempts.size() == 0) {
            return "Not-loaded Resource State " + state;
        } else if (foundFile != null) {
            return "State " + state + " not found in " + foundFile;
        }

        StringBuilder msg = new StringBuilder("Failed to load resource state ");
        msg.append(state);
        msg.append(". Attempted to load from ");
        for (int i = 0; i < attempts.size(); ++i) {
            if (i > 0)
                msg.append(", ");
            msg.append("[");
            msg.append(attempts.get(i));
            msg.append("]");
        }
        return msg.toString();
    }

    /**
     * Load the configured resource state. Use this method only once. call
     * isLoaded() to discover success or failure
     */
    @Override
    public void load(String state) {

        String tmpResourceStateName = state;
        String tmpResourceName = tmpResourceStateName;

        if (tmpResourceName.contains("-")) {
            tmpResourceName = tmpResourceName.substring(0, tmpResourceName.indexOf("-"));
        }

        String beanXml = "IRIS-" + tmpResourceName + "-PRD.xml";

        // Attempt to create Spring context based on current resource filename
        // pattern
        ApplicationContext context = createApplicationContext(beanXml);

        if (context == null) {
            // Failed to create Spring context using current resource filename
            // pattern so use old pattern
            int pos = tmpResourceName.lastIndexOf("_");

            if (pos > 3) {
                tmpResourceName = tmpResourceName.substring(0, pos);
                beanXml = "IRIS-" + tmpResourceName + "-PRD.xml";

                context = createApplicationContext(beanXml);

                if (context != null) {
                    // Successfully created Spring context using old resource
                    // filename pattern

                    // Convert resource state name to old resource name format
                    pos = tmpResourceStateName.lastIndexOf("-");

                    if (pos < 0) {
                        pos = tmpResourceStateName.lastIndexOf("_");

                        if (pos > 0) {
                            tmpResourceStateName = tmpResourceStateName.substring(0, pos) + "-"
                                    + tmpResourceStateName.substring(pos + 1);
                        }
                    }
                }
            }
        }

        if (context != null) {
            result = loadAllResourceStatesFromFile(context, tmpResourceStateName);
        }
        if (result == null) {
            List<String> timestampedFiles = getTimestampedResourceStateFileLists(tmpResourceName);
            if (!timestampedFiles.isEmpty()) {
                result = loadAllResourceStatesFromTimeStampedResourceState(tmpResourceStateName, timestampedFiles);
            }
        }
    }

    @Override
    public void setIrisConfigDirPath(String location) {
        if (this.configLoader == null) {
            this.configLoader = new ConfigLoader();
        }
        this.configLoader.setIrisConfigDirPath(location);
    }

    /**
     * @param configLoader
     *            the configLoader to set
     */
    public void setConfigLoader(AbstractConfigLoaders configLoader) {
        this.configLoader = configLoader;
    }
}