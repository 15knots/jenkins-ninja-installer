/*******************************************************************************
 * Copyright (c) 2015 Martin Weber.
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package org.jenkinsci.plugins.ninja;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.TaskListener;
import hudson.model.DownloadService.Downloadable;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.tools.ToolInstaller;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.MasterToSlaveFileCallable;
import net.sf.json.JSONObject;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Automatic Ninja installer.
 */
public class NinjaInstaller extends DownloadFromUrlInstaller {
    private static Logger logger = Logger.getLogger(NinjaInstaller.class
            .getName());

    @DataBoundConstructor
    public NinjaInstaller(String id) {
        super(id);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node,
            TaskListener log) throws IOException, InterruptedException {
        // Gather properties for the node to install on
        final String[] nodeProperties = node.getChannel().call(
                new GetSystemProperties("os.name", "os.arch"));

        final Installable inst = getInstallable(nodeProperties[0], nodeProperties[1]);
        if (inst == null) {
            String msg = String
                    .format("%s [%s]: No tool download known for OS `%s` and arch `%s`.",
                            getDescriptor().getDisplayName(), tool.getName(),
                            nodeProperties[0], nodeProperties[1]);
            throw new AbortException(msg);
        }

        final FilePath toolPath = getFixedPreferredLocation(tool, node);
        if (!isUpToDate(toolPath, inst)) {
            if (toolPath.installIfNecessaryFrom(
                    new URL(inst.url),
                    log,
                    "Unpacking " + inst.url + " to " + toolPath + " on "
                            + node.getDisplayName())) {
                // we don't use the timestamp..
                toolPath.child(".timestamp").delete();
                // pull up extra subdir...
                FilePath base = findPullUpDirectory(toolPath);
                if (base != null && !base.equals(toolPath)) {
                    base.moveAllChildrenTo(toolPath);
                }
                // needed for executable flag
                toolPath.act(new ChmodRecAPlusX());
                // leave a record for the next up-to-date check
                toolPath.child(".installedFrom").write(inst.url, "UTF-8");
            }
        }

        return toolPath.child("ninja");
    }

    /**
     * Overloaded to select the OS-ARCH specific variant and to fill in the
     * variant´s URL.
     *
     * @param nodeOsName
     *            the value of the JVM system property "os.name" of the node
     * @param nodeOsArch
     *            the value of the JVM system property "os.arch" of the node
     * @return null if no such matching variant is found.
     */
    public Installable getInstallable(String nodeOsName, String nodeOsArch)
            throws IOException {
        List<NinjaInstallable> installables = ((DescriptorImpl) getDescriptor())
                .getInstallables();

        for (NinjaInstallable inst : installables)
            if (id.equals(inst.id)) {
                // Filter variants to install by system-properties
                // for the node to install on
                OsFamily osFamily = OsFamily.valueOfOsName(nodeOsName);
                for (NinjaVariant variant : inst.variants) {
                    if (variant.appliesTo(osFamily, nodeOsArch)) {
                        // fill in URL for download machinery
                        inst.url = variant.url;
                        return inst;
                    }
                }
            }
        return null;
    }

    /**
     * Fixes the value returned by {@link ToolInstaller#preferredLocation} to
     * use the <strong>installer ID</strong> instead of the ToolInstallation
     * {@link ToolInstallation#getName name}. This fix avoids unneccessary
     * downloads when users change the name of the tool on the global config
     * page.
     *
     * @param tool
     *            the tool being installed
     * @param node
     *            the computer on which to install the tool
     *
     * @return a fixed file path (a path within the local Jenkins work area), if
     *         {@code tool#getHome()} is {@code null}, else the unchanged
     *         {@code ToolInstaller#preferredLocation()}
     */
    private FilePath getFixedPreferredLocation(ToolInstallation tool, Node node) {
        final FilePath toolPath = preferredLocation(tool, node);
        if (tool.getHome() == null) {
            // jenkins wants to download, having preferredLocation jam in
            // the NAME instead of the ID
            return toolPath.sibling(sanitize(id));
        }
        return toolPath;
    }

    private static String sanitize(String s) {
        return s != null ? s.replaceAll("[^A-Za-z0-9_.-]+", "_") : null;
    }

    // //////////////////////////////////////////////////////////////////
    // inner classes
    // //////////////////////////////////////////////////////////////////
    /**
     * Sets execute permission on all files, since unzip etc. might not do this.
     * Hackish, is there a better way?
     */
    private static class ChmodRecAPlusX extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;
        public Void invoke(File d, VirtualChannel channel) throws IOException {
            if(!Functions.isWindows())
                process(d);
            return null;
        }
        private void process(File f) {
            if (f.isFile()) {
                f.setExecutable(true, false);
            } else {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File kid : kids) {
                        process(kid);
                    }
                }
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends
            DownloadFromUrlInstaller.DescriptorImpl<NinjaInstaller> {
        @Override
        public String getDisplayName() {
            return "Install from github ninja releases";
        }

        /**
         * List of installable tools.
         *
         * <p>
         * The UI uses this information to populate the drop-down.
         *
         * @return never null.
         */
        @Override
        public List<NinjaInstallable> getInstallables() throws IOException {
            JSONObject d = Downloadable.get(getId()).getData();
            if (d == null)
                return Collections.emptyList();
            Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();
            classMap.put("variants", NinjaVariant.class);
            return Arrays.asList(((NinjaInstallableList) JSONObject.toBean(d,
                    NinjaInstallableList.class, classMap)).list);
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == NinjaTool.class;
        }
    } // DescriptorImpl

    private static enum OsFamily {
        Linux("linux"), Windows("win"), OSX("mac");
        private final String downloadSiteName;

        /**
         * Gets the OS name as specified in the files on the download
         * site.
         *
         * @return the current downloadSiteName property.
         */
        public String getDownloadSiteName() {
            return downloadSiteName != null ? downloadSiteName : name();
        }

        private OsFamily() {
            this(null);
        }

        private OsFamily(String downloadSiteName) {
            this.downloadSiteName = downloadSiteName;
        }

        /**
         * Gets the OS family from the value of the system property "os.name".
         *
         * @param osName
         *            the value of the system property "os.name"
         * @return the OsFalimly object or {@code null} if osName is unknown
         */
        public static OsFamily valueOfOsName(String osName) {
            if (osName != null) {
                if ("Linux".equals(osName)) {
                    return Linux;
                } else if (osName.startsWith("Windows")) {
                    return Windows;
                } else if (osName.contains("OS X")) {
                    return OSX;
                }
            }
            return null;
        }
    } // OsFamily

    // //////////////////////////////////////////////////////////////////
    // JSON deserialization
    // //////////////////////////////////////////////////////////////////
    /**
     * Represents the de-serialized JSON data file containing all installable
     * Ninja versions. See the jenkins crawler output for details.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static final class NinjaInstallableList {
        // initialize with an empty array just in case JSON doesn't have the
        // list field (which shouldn't happen.)
        // Public for JSON deserialisation
        public NinjaInstallable[] list = new NinjaInstallable[0];
    } // NinjaInstallableList

    // Needs to be public for JSON deserialisation
    @Restricted(NoExternalUse.class)
    public static class NinjaVariant {
        public String url;
        // these come frome the JSON file and finally from ninja´s download site
        // URLs
        /** OS name as specified by the download site */
        public String os = "";
        /** OS architecture as specified by the download site */
        public String arch = "";

        /**
         * Checks whether an installation of this NinjaVariant will work on the
         * given node. This checks the given JVM system properties of a node.
         *
         * @param osFamily
         *            the OS family derived from the JVM system property
         *            "os.name" of the node
         * @param nodeOsArch
         *            the value of the JVM system property "os.arch" of the node
         */
        public boolean appliesTo(OsFamily osFamily, String nodeOsArch) {
            if (osFamily != null && osFamily.getDownloadSiteName().equals(os)) {
              // only "-" archs are provided by download site ATM
                switch (osFamily) {
                case Linux:
                        return true;
                case OSX:
                        return true;
                case Windows:
                    return true;
                default:
                    break;
                }
            }
            return false;
        }
    }

    // Needs to be public for JSON deserialisation
    @Restricted(NoExternalUse.class)
    public static class NinjaInstallable extends Installable {
        public NinjaVariant[] variants = new NinjaVariant[0];

        /**
         * Default ctor for JSON de-serialization.
         */
        public NinjaInstallable() {
        }

    }
}