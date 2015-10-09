#!./lib/runner.groovy
// Generates server-side metadata for ninja auto-installation
import com.gargoylesoftware.htmlunit.html.*;
import net.sf.json.*
import com.gargoylesoftware.htmlunit.WebClient

def githubUrl= 'https://github.com';
def projectRelURL= '/martine/ninja/releases';

def wc = new WebClient()
wc.setJavaScriptEnabled(false);

def relArchives = [];
def releases = [:]

// Gather a list of archive files relative to githubUrl
wc.getPage(githubUrl+ projectRelURL).selectNodes("//a[@href]").each { HtmlAnchor e ->
  def rel=  e.getHrefAttribute()
  // We only want release archives; ignore source packages and other
  def m = (rel =~ /^/ + projectRelURL + /\/download\/v\d+(\.\d+)*\/ninja-(.*).zip$/)
  if (m) {
    relArchives << rel;
  }
}

// Build a map of Ninja versions -> platform archives
relArchives.each { file ->
  // Extract the version info from archive filename
  def parts = (file =~ /.+\/download\/v(\d+(\.\d+)*)\/ninja-(.*).zip$/)
  if (parts) {
    // Gather the info for this archive
    def variant = [:]
    variant.url = githubUrl+ file;
    variant.os = parts[0][3]
    variant.arch = "-"; // ninja URLs do not specify an architecture as of Sep 2015
//    println variant;

    // Add it to the list of variants for this version of ninja
    def version = parts[0][1]
    if (!releases[version]) {
      releases[version] = []
    }
    releases[version] << variant
  }
}

// Build the JSON structure: a list of release objects, each with its platform-specific variants
def json = [list: releases.collect { key, value ->
    [ 'id': key, 'name': "${key}".toString(),
      'variants': value] }]
// Write the JSON update file
lib.DataWriter.write('org.jenkinsci.plugins.ninja.NinjaInstaller', JSONObject.fromObject(json));
