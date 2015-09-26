/*******************************************************************************
 * Copyright (c) 2015 Martin Weber.
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package org.jenkinsci.plugins.ninja;

import jenkins.security.MasterToSlaveCallable;

/**
 * A Callable that gets the values of the given Java system properties from
 * the (remote) node.
 */
public class GetSystemProperties extends
        MasterToSlaveCallable<String[], InterruptedException> {
    private static final long serialVersionUID = 1L;

    private final String[] properties;

    GetSystemProperties(String... properties) {
        this.properties = properties;
    }

    public String[] call() {
        String[] values = new String[properties.length];
        for (int i = 0; i < properties.length; i++) {
            values[i] = System.getProperty(properties[i]);
        }
        return values;
    }
}