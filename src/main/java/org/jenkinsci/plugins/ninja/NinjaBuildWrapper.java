/*******************************************************************************
 * Copyright (c) 2015 Martin Weber.
 *
 * Contributors:
 *      Martin Weber - Initial implementation
 *******************************************************************************/
package org.jenkinsci.plugins.ninja;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tools.InstallSourceProperty;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.Map;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A Build wrapper that download the ninja build system on demand and adds the
 * ninja commands to PATH so that sub-processes of Jenkins can invoke ninja.
 *
 * @author Martin Weber
 */
public class NinjaBuildWrapper extends BuildWrapper {

  /** the name of the nija tool installation to use for this job */
  private String installationName;

  @DataBoundConstructor
  public NinjaBuildWrapper(String installationName) {
    this.installationName = installationName;
  }

  @Override
  public BuildWrapper.Environment setUp(
      @SuppressWarnings("rawtypes") AbstractBuild build, Launcher launcher,
      BuildListener listener) throws IOException, InterruptedException {
    NinjaTool installation = getSelectedInstallation();
    // Raise an error if the ninja installation isn't found
    if (installation == null) {
      listener.fatalError("There is no Ninja installation selected."
          + " Please review the job configuration.");
      return null;
    }
//    EnvVars env = build.getEnvironment(listener);
//??    env.overrideAll(build.getBuildVariables());

    // Get the ninja version for this node, installing it if necessary
    installation = (NinjaTool) installation.translate(build, listener);

    // add ninja to PATH for sub-processes, if autoinstalled
    if (installation.getProperties().get(InstallSourceProperty.class) != null) {
      final NinjaTool install = installation;
      return new Environment() {
        @Override
        public void buildEnvVars(Map<String, String> env) {
            EnvVars envVars = new EnvVars();
            install.buildEnvVars(envVars);
            env.putAll(envVars);
        }
      };
    } else {
      return new Environment() {
        @Override
        public void buildEnvVars(Map<String, String> env) {
        }
      };
    }
  }

  /**
   * Finds the Ninja tool installation to use for this build among all
   * installations configured in the Jenkins administration
   *
   * @return selected Ninja installation or {@code null} if none could be found
   */
  private NinjaTool getSelectedInstallation() {
    NinjaTool.DescriptorImpl descriptor = (NinjaTool.DescriptorImpl) Jenkins
        .getInstance().getDescriptor(NinjaTool.class);
    for (NinjaTool i : descriptor.getInstallations()) {
      if (installationName != null && i.getName().equals(installationName))
        return i;
    }

    return null;
  }

  @Extension
  public static class DescriptorImpl extends BuildWrapperDescriptor {

    public DescriptorImpl() {
      load();
    }

    @Override
    public String getDisplayName() {
      return "Set up ninja build tool";
    }

    @Override
    public boolean isApplicable(AbstractProject<?, ?> item) {
      return true;
    }
    /**
     * Determines the values of the Ninja installation drop-down list box.
     */
    public ListBoxModel doFillInstallationNameItems() {
        ListBoxModel items = new ListBoxModel();
        NinjaTool.DescriptorImpl descriptor = (NinjaTool.DescriptorImpl) Jenkins
                .getInstance().getDescriptor(NinjaTool.class);
        for (NinjaTool inst : descriptor.getInstallations()) {
            items.add(inst.getName());// , "" + inst.getPid());
        }
        return items;
    }
  }

}
