/*******************************************************************************
 * Copyright (c) 2015 Martin Weber.
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package org.jenkinsci.plugins.ninja;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Information about Ninja installation. A NinjaTool is used to select between
 * different installations of ninja, as in "ninja2.6.4" or "ninja2.8.12".
 *
 * @author Martin Weber
 */
public class NinjaTool extends ToolInstallation implements
        NodeSpecific<NinjaTool>, EnvironmentSpecific<NinjaTool> {
    // private static final Logger LOGGER = Logger.getLogger(NinjaTool.class
    // .getName());

    /**
     * Tool name of the default tool (usually found on the executable search
     * path). Do not use: Exposed here only for testing purposes.
     */
    public static transient final String DEFAULT = "InSearchPath";

    private static final long serialVersionUID = 1;

    /**
     * Constructor for NinjaTool.
     *
     * @param name
     *            Tool name (for example, "ninja2.6.4" or "ninja2.8.12")
     * @param home
     *            Tool location (usually "ninja")
     * @param properties
     *            {@link java.util.List} of properties for this tool
     */
    @DataBoundConstructor
    public NinjaTool(String name, String home,
            List<? extends ToolProperty<?>> properties) {
        super(Util.fixEmptyAndTrim(name), Util.fixEmptyAndTrim(home),
                properties);
    }

    /**
     * Overwritten to add the path to ninja`s bin directory, if tool was
     * downloaded.
     */
    @Override
    public void buildEnvVars(EnvVars env) {
        if (getProperties().get(InstallSourceProperty.class) != null) {
            // ninja was downloaded and installed
            String home = getHome(); // the home on the slave!!!
            if (home != null) {
                // home= dirname(home) as a cross-platform version...
                int idx;
                if ((idx = home.lastIndexOf('/')) != -1
                        || (idx = home.lastIndexOf('\\')) != -1 && idx > 1) {
                    env.put("PATH+NINJA", home.substring(0, idx));
                }
            }
        }
    }

    public NinjaTool forNode(Node node, TaskListener log) throws IOException,
            InterruptedException {
        return new NinjaTool(getName(), translateFor(node, log),
                getProperties().toList());
    }

    public NinjaTool forEnvironment(EnvVars environment) {
        return new NinjaTool(getName(), environment.expand(getHome()),
                getProperties().toList());
    }

    /**
     * Creates a default tool installation if needed. Uses "ninja" or migrates
     * data from previous versions
     *
     */
    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void onLoaded() {

        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            return;
        }
        DescriptorImpl descriptor = (DescriptorImpl) jenkinsInstance
                .getDescriptor(NinjaTool.class);
        NinjaTool[] installations = getInstallations(descriptor);

        if (installations != null && installations.length > 0) {
            // No need to initialize if there's already something
            return;
        }

        NinjaTool tool = new NinjaTool(DEFAULT, "ninja",
                Collections.<ToolProperty<?>> emptyList());
        descriptor.setInstallations(new NinjaTool[] { tool });
        descriptor.save();
    }

    private static NinjaTool[] getInstallations(DescriptorImpl descriptor) {
        NinjaTool[] installations = null;
        try {
            installations = descriptor.getInstallations();
        } catch (NullPointerException e) {
            installations = new NinjaTool[0];
        }
        return installations;
    }

    // //////////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////////

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<NinjaTool> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Ninja";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json)
                throws FormException {
            // reject empty tool names...
            List<NinjaTool> ninjas = req.bindJSONToList(NinjaTool.class,
                    json.get("tool"));
            for (NinjaTool tool : ninjas) {
                if (Util.fixEmpty(tool.getName()) == null)
                    throw new FormException(getDisplayName()
                            + " installation requires a name", "_.name");
            }

            super.configure(req, json);
            save();
            return true;
        }

        /**
         * Overwritten to make ninja auto-installer a default option.
         */
        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new NinjaInstaller(null));
        }

    } // DescriptorImpl
}
