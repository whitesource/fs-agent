package org.whitesource.fs;

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.ConfigPropertyKeys;
import org.whitesource.agent.Constants;

import java.util.Properties;

/*
 * FSA Configuration File Properties Helper.
 */
public class FSAConfigProperties extends Properties {

    @Override
    public synchronized Object setProperty(String key, String value) {
        return super.setProperty(key.toLowerCase(), value);
    }

    @Override
    public String getProperty(String key) {

        return super.getProperty(key.toLowerCase());
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        if (key instanceof String) {
            return super.put(((String) key).toLowerCase(), value);
        } else {
            return super.put(key, value);
        }
    }

    @Override
    public synchronized Object get(Object key) {
        if (key instanceof String) {
            return super.get(((String) key).toLowerCase());
        } else {
            return super.get(key);
        }
    }


    public int getIntProperty(String propertyKey, int defaultValue) {
        int value = defaultValue;
        String propertyValue = getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            try {
                value = Integer.valueOf(propertyValue);
            } catch (NumberFormatException e) {
                // do nothing
            }
        }
        return value;
    }

    public boolean getBooleanProperty(String propertyKey, boolean defaultValue) {
        boolean property = defaultValue;
        String propertyValue = getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            property = Boolean.valueOf(propertyValue);
        }
        return property;
    }

    public boolean getBooleanProperty(String propertyKey, String dominantPropertyKey, boolean defaultValue) {
        boolean property = defaultValue;
        String propertyValue = getProperty(propertyKey);
        String dominantPropertyValue = getProperty(dominantPropertyKey);
        if (StringUtils.isNotBlank(dominantPropertyValue)) {
            property = Boolean.valueOf(dominantPropertyValue);
        } else if (StringUtils.isNotBlank(propertyValue)) {
            property = Boolean.valueOf(propertyValue);
        }
        return property;
    }

    public long getLongProperty(String propertyKey, long defaultValue) {
        long property = defaultValue;
        String propertyValue = getProperty(propertyKey);
        if (StringUtils.isNotBlank(propertyValue)) {
            property = Long.parseLong(propertyValue);
        }
        return property;
    }

    public String[] getListProperty(String propertyName, String[] defaultValue) {
        String property = getProperty(propertyName);
        if (property == null) {
            return defaultValue;
        }
        return property.split(Constants.WHITESPACE);
    }

    public String[] getPythonIncludesWithPipfile(String propertyName, String[] defaultValue) {
        String property = getProperty(propertyName);
        if (property == null) {
            return defaultValue;
        }
        property = property + Constants.WHITESPACE + Constants.PIPFILE;
        return property.split(Constants.WHITESPACE);
    }


    public int getArchiveDepth() {
        return getIntProperty(ConfigPropertyKeys.ARCHIVE_EXTRACTION_DEPTH_KEY, FSAConfiguration.DEFAULT_ARCHIVE_DEPTH);
    }

    public String[] getIncludes() {
        String includesString = getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING);
        if (StringUtils.isNotBlank(includesString)) {
            return getProperty(ConfigPropertyKeys.INCLUDES_PATTERN_PROPERTY_KEY, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    public String[] getPythonIncludes() {
        String includesString = getProperty(ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, Constants.PYTHON_REQUIREMENTS);
        if (StringUtils.isNotBlank(includesString)) {
            return getProperty(ConfigPropertyKeys.PYTHON_REQUIREMENTS_FILE_INCLUDES, Constants.PYTHON_REQUIREMENTS).split(Constants.WHITESPACE);
        }
        return new String[0];
    }

    public String[] getProjectPerFolderIncludes() {
        String projectPerFolderIncludesString = getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_INCLUDES, null);
        if (StringUtils.isNotBlank(projectPerFolderIncludesString)) {
            return getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_INCLUDES, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        if (Constants.EMPTY_STRING.equals(projectPerFolderIncludesString)) {
            return null;
        }
        String[] result = new String[1];
        result[0] = "*";
        return result;
    }

    public String[] getProjectPerFolderExcludes() {
        String projectPerFolderExcludesString = getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_EXCLUDES, Constants.EMPTY_STRING);
        if (StringUtils.isNotBlank(projectPerFolderExcludesString)) {
            return getProperty(ConfigPropertyKeys.PROJECT_PER_FOLDER_EXCLUDES, Constants.EMPTY_STRING).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
        }
        return new String[0];
    }

    public String[] getDockerIncludes() {
        String includesString = getProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, Constants.GLOB_PATTERN);
        if (StringUtils.isEmpty(includesString)) {
            setProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, Constants.GLOB_PATTERN);
        }
        return getProperty(ConfigPropertyKeys.DOCKER_INCLUDES_PATTERN_PROPERTY_KEY, Constants.GLOB_PATTERN).split(FSAConfiguration.INCLUDES_EXCLUDES_SEPARATOR_REGEX);
    }
}
