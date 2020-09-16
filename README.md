# Ninja build system plugin for Jenkins
Automatically installs the ninja build system on a Jenkins build node during a build.

For the selected version, the correct package for the build machine's operating system, version and CPU architecture is 
automatically selected. Ninja is available for Linux, Mac OS and Windows.

Once installed, the `PATH` is set appropriately, so that the `ninja` command is available during a build.

## NOTE: This plugin is experimental and dormant
