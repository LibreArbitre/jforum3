/*
 * Copyright (c) JForum Team
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met:
 * 
 * 1) Redistributions of source code must retain the above 
 * copyright notice, this list of conditions and the 
 * following  disclaimer.
 * 2)  Redistributions in binary form must reproduce the 
 * above copyright notice, this list of conditions and 
 * the following disclaimer in the documentation and/or 
 * other materials provided with the distribution.
 * 3) Neither the name of "Rafael Steil" nor 
 * the names of its contributors may be used to endorse 
 * or promote products derived from this software without 
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT 
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER 
 * IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 * 
 * Created on 02/11/2004 12:45:37
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.jforum.api.integration.mail.pop.POPJobStarter;
import net.jforum.cache.CacheEngine;
import net.jforum.cache.Cacheable;
import net.jforum.dao.DataAccessDriver;
import net.jforum.exceptions.CacheEngineStartupException;
import net.jforum.exceptions.ForumException;
import net.jforum.search.SearchFacade;
import net.jforum.sso.LoginAuthenticator;
import net.jforum.summary.SummaryScheduler;
import net.jforum.util.FileMonitor;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.QueriesFileListener;
import net.jforum.util.preferences.SystemGlobals;
import net.jforum.util.preferences.SystemGlobalsListener;

import org.apache.log4j.Logger;
import org.quartz.SchedulerException;

/**
 * General utilities methods for loading configurations for JForum.
 * 
 * @author Rafael Steil
 * @version $Id: ConfigLoader.java,v 1.30 2007/07/27 15:42:56 rafaelsteil Exp $
 */
public class ConfigLoader
{
        private static final Logger logger = Logger.getLogger(ConfigLoader.class);
        private static CacheEngine cache;
        private static PropertiesLoader propertiesLoader = new DefaultPropertiesLoader();
        private static ReflectionProvider reflectionProvider = new DefaultReflectionProvider();
	
	/**
	 * Start ( or restart ) <code>SystemGlobals</code>.
	 * This method loads all configuration keys set at
	 * <i>SystemGlobals.properties</i>, <i>&lt;user.name&gt;.properties</i>
	 * and database specific stuff.
	 * 
	 * @param appPath The application root's directory
	 */
	public static void startSystemglobals(String appPath)
	{
		SystemGlobals.initGlobals(appPath, appPath + "/WEB-INF/config/SystemGlobals.properties");
		SystemGlobals.loadAdditionalDefaults(SystemGlobals.getValue(ConfigKeys.DATABASE_DRIVER_CONFIG));
		
		if (new File(SystemGlobals.getValue(ConfigKeys.INSTALLATION_CONFIG)).exists()) {
			SystemGlobals.loadAdditionalDefaults(SystemGlobals.getValue(ConfigKeys.INSTALLATION_CONFIG));
		}
	}

	/**
	 * Loads module mappings for the system.
	 * 
	 * @param baseConfigDir The directory where the file <i>modulesMapping.properties</i> is.
	 * @return The <code>java.util.Properties</code> instance, with the loaded modules 
	 */
        public static Properties loadModulesMapping(String baseConfigDir)
        {
                String filePath = baseConfigDir + "/modulesMapping.properties";

                try {
                        return propertiesLoader.load(filePath);
                }
                catch (IOException e) {
                        throw new ForumException("Error while loading modules mapping from " + filePath, e);
                }
    }
	
        public static void createLoginAuthenticator()
        {
                String className = SystemGlobals.getValue(ConfigKeys.LOGIN_AUTHENTICATOR);

                try {
                        LoginAuthenticator loginAuthenticator = reflectionProvider.newInstance(className, LoginAuthenticator.class);
                        SystemGlobals.setObjectValue(ConfigKeys.LOGIN_AUTHENTICATOR_INSTANCE, loginAuthenticator);
                }
                catch (Exception e) {
                        throw new ForumException("Error while trying to create a login.authenticator instance ("
                                + className + "): " + e.getMessage(), e);
                }
        }
	
	/**
	 * Load url patterns.
	 * The method tries to load url patterns from <i>WEB-INF/config/urlPattern.properties</i>
	 */
        public static void loadUrlPatterns()
        {
                String filePath = SystemGlobals.getValue(ConfigKeys.CONFIG_DIR) + "/urlPattern.properties";

                try {
                        Properties p = propertiesLoader.load(filePath);

                        Map<String, String> patterns = new HashMap<String, String>();
                        for (String name : p.stringPropertyNames()) {
                                patterns.put(name, p.getProperty(name));
                        }

                        for (Map.Entry<String, String> entry : patterns.entrySet()) {
                                UrlPatternCollection.addPattern(entry.getKey(), entry.getValue());
                        }
                }
                catch (IOException e) {
                        throw new ForumException("Error while loading url patterns from " + filePath, e);
                }
    }
	
	/**
	 * Listen for changes in common configuration files.
	 * The watched files are: <i>generic_queries.sql</i>, 
	 * <i>&lt;database_name&gt;.sql</i>, <i>SystemGlobals.properties</i>
	 * and <i>&lt;user.name&gt;.properties</i>
	 */
	public static void listenForChanges()
	{
		int fileChangesDelay = SystemGlobals.getIntValue(ConfigKeys.FILECHANGES_DELAY);
		
		if (fileChangesDelay > 0) {
			// Queries
			FileMonitor.getInstance().addFileChangeListener(new QueriesFileListener(),
				SystemGlobals.getValue(ConfigKeys.SQL_QUERIES_GENERIC), fileChangesDelay);

			FileMonitor.getInstance().addFileChangeListener(new QueriesFileListener(),
				SystemGlobals.getValue(ConfigKeys.SQL_QUERIES_DRIVER), fileChangesDelay);

			// System Properties
			FileMonitor.getInstance().addFileChangeListener(new SystemGlobalsListener(),
				SystemGlobals.getValue(ConfigKeys.DEFAULT_CONFIG), fileChangesDelay);

			ConfigLoader.listenInstallationConfig();
        }
	}
	
	public static void listenInstallationConfig()
	{
		int fileChangesDelay = SystemGlobals.getIntValue(ConfigKeys.FILECHANGES_DELAY);
		
		if (fileChangesDelay > 0) {
			if (new File(SystemGlobals.getValue(ConfigKeys.INSTALLATION_CONFIG)).exists()) {
				FileMonitor.getInstance().addFileChangeListener(new SystemGlobalsListener(),
						SystemGlobals.getValue(ConfigKeys.INSTALLATION_CONFIG), fileChangesDelay);
			}
		}
	}
	
        public static void loadDaoImplementation()
        {
                // Start the dao.driver implementation
                String driver = SystemGlobals.getValue(ConfigKeys.DAO_DRIVER);

                logger.info("Loading JDBC driver " + driver);

                try {
                        DataAccessDriver d = reflectionProvider.newInstance(driver, DataAccessDriver.class);
                        DataAccessDriver.init(d);
                }
                catch (Exception e) {
                        throw new ForumException("Error while trying to create DAO implementation (" + driver + "): "
                                        + e.getMessage(), e);
                }
    }
	
        public static void startCacheEngine()
        {
                try {
                        String cacheImplementation = SystemGlobals.getValue(ConfigKeys.CACHE_IMPLEMENTATION);
                        logger.info("Using cache engine: " + cacheImplementation);

                        cache = reflectionProvider.newInstance(cacheImplementation, CacheEngine.class);
                        cache.init();
			
			String s = SystemGlobals.getValue(ConfigKeys.CACHEABLE_OBJECTS);
			if (s == null || s.trim().equals("")) {
				logger.warn("Cannot find Cacheable objects to associate the cache engine instance.");
				return;
			}
			
			String[] cacheableObjects = s.split(",");
                        for (int i = 0; i < cacheableObjects.length; i++) {
                                logger.info("Creating an instance of " + cacheableObjects[i]);
                                Cacheable cacheable = reflectionProvider.newInstance(cacheableObjects[i].trim(), Cacheable.class);
                                cacheable.setCacheEngine(cache);
                        }
                }
		catch (Exception e) {
			throw new CacheEngineStartupException("Error while starting the cache engine", e);
		}
	}
	
	public static void stopCacheEngine()
	{
		if (cache != null) {
			cache.stop();
		}
	}
	
	public static void startSearchIndexer()
	{
		SearchFacade.init();
	}

	/**
	 * Init a Job who will send e-mails to the all users with a summary of posts...
	 * @throws SchedulerException
	 * @throws IOException
	 */
	public static void startSummaryJob() throws SchedulerException {
		SummaryScheduler.startJob();
	}
	
        public static void startPop3Integration() throws SchedulerException
        {
                POPJobStarter.startJob();
        }

        static void setPropertiesLoader(PropertiesLoader loader)
        {
                propertiesLoader = loader == null ? new DefaultPropertiesLoader() : loader;
        }

        static void setReflectionProvider(ReflectionProvider provider)
        {
                reflectionProvider = provider == null ? new DefaultReflectionProvider() : provider;
        }

        interface PropertiesLoader
        {
                Properties load(String path) throws IOException;
        }

        static class DefaultPropertiesLoader implements PropertiesLoader
        {
                public Properties load(String path) throws IOException
                {
                        try (FileInputStream fis = new FileInputStream(path)) {
                                Properties properties = new Properties();
                                properties.load(fis);
                                return properties;
                        }
                }
        }

        interface ReflectionProvider
        {
                <T> T newInstance(String className, Class<T> expectedType) throws ReflectiveOperationException;
        }

        static class DefaultReflectionProvider implements ReflectionProvider
        {
                public <T> T newInstance(String className, Class<T> expectedType) throws ReflectiveOperationException
                {
                        Class<?> clazz = Class.forName(className);

                        if (!expectedType.isAssignableFrom(clazz)) {
                                throw new ClassCastException("Class " + className + " is not assignable to " + expectedType.getName());
                        }

                        Object instance = clazz.getDeclaredConstructor().newInstance();
                        return expectedType.cast(instance);
                }
        }
}
