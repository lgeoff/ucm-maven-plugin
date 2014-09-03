package org.ucmtwine.maven.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import oracle.stellent.ridc.model.DataBinder;
import oracle.stellent.ridc.model.DataObject;
import oracle.stellent.ridc.model.DataResultSet;
import oracle.stellent.ridc.model.impl.DataFactoryImpl;
import oracle.stellent.ridc.model.serialize.HdaBinderSerializer;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Build the component zip
 * 
 * @goal build
 */
public class BuildComponent extends AbstractComponentMojo {

  private static final int BUFFER = 2048;

  /**
   * The component zip path, relative to the root of the project.
   * 
   * @parameter expression="${project.basedir}/manifest.hda" default=""
   */
  private File manifestFile;

  /**
   * A filter of file and directory names to exclude when packaging the
   * component.
   * 
   * @parameter default="\.svn|\.git|\._.*|\.DS_Store|thumbs\.db|lockwait\. dat"
   */
  private String excludeFiles;

    /**
     * Component folder
     *
     * @parameter default-value=""
     */
    private String componentFolder;

  public void execute() throws MojoExecutionException, MojoFailureException {

    determineComponentName();
    determineComponentZip();

    DataResultSet manifestRs = getResultSetFromHda(manifestFile, "Manifest");

    ZipOutputStream zipStream;

    Map<String, String> zipListing = new TreeMap<String, String>();

    zipListing.put(new File(componentFolder,"manifest.hda").toString(), "manifest.hda");
    for (DataObject row : manifestRs.getRows()) {
      addToZipList(zipListing, row);
    }

    if (componentName == null) {
      throw new MojoExecutionException("No component name specified or auto detected");
    }

    getLog().info("Saving " + componentZip.getName() + " with contents:");

    try {
      zipStream = new ZipOutputStream(new FileOutputStream(componentZip, false));

    } catch (FileNotFoundException e) {
      throw new MojoExecutionException("Unable to open zip file for output", e);
    }

    for (Iterator<String> i = zipListing.keySet().iterator(); i.hasNext();) {
      String fileSystemPath = i.next();
      String zipPath = zipListing.get(fileSystemPath);
      getLog().info("  " + zipPath);

      try {
        addFileToZip(zipStream, new File(fileSystemPath), zipPath);
      } catch (IOException e) {
        throw new MojoExecutionException("Unable to close stream for: " + fileSystemPath, e);
      }
    }

    try {
      zipStream.close();

    } catch (IOException e) {
      throw new MojoExecutionException("Unable to close zip file", e);
    }
  }

  /**
   * Add the file to the zip output stream
   * 
   * @param zipStream
   * @param fileSystemPath
   * @param zipPath
   * @throws MojoExecutionException
   * @throws IOException
   */
  private void addFileToZip(ZipOutputStream zipStream, File fileSystemPath, String zipPath)
      throws MojoExecutionException, IOException {

    if (!fileSystemPath.canRead()) {
      throw new MojoExecutionException("file cannot be read: " + fileSystemPath);
    }

    if (fileSystemPath.isDirectory()) {
      addFolderToZip(zipStream, fileSystemPath, zipPath);

    } else {

      InputStream in = null;
      try {
        in = new FileInputStream(fileSystemPath);

        ZipEntry entry = new ZipEntry(zipPath);
        zipStream.putNextEntry(entry);

        byte[] buf = new byte[BUFFER];
        int num = 0;
        while ((num = in.read(buf)) > 0) {
          zipStream.write(buf, 0, num);
        }

      } catch (FileNotFoundException e) {
        throw new MojoExecutionException("file not found: " + fileSystemPath);

      } catch (IOException e) {
        throw new MojoExecutionException("error writing to zip: " + fileSystemPath);

      } finally {
        in.close();
        zipStream.closeEntry();
      }
    }
  }

  private void addFolderToZip(ZipOutputStream zipStream, File fileSystemPath, String zipPath)
      throws MojoExecutionException, IOException {
    // get all items in folder, exclude those in excludeFiles
    if (zipPath.endsWith("/") || zipPath.endsWith("\\")) {
      zipPath = zipPath.substring(0, zipPath.length() - 1);
    }

    // It is also possible to filter the list of returned files.
    // This example does not return any files that start with `.'.
    FilenameFilter filter = getFileFilter();

    for (File entry : fileSystemPath.listFiles(filter)) {
      String newZipPath = zipPath + "/" + entry.getName();
      if (entry.isDirectory()) {
        addFolderToZip(zipStream, entry, newZipPath);
      } else {
        addFileToZip(zipStream, entry, newZipPath);
      }
    }
  }

  /**
   * Return the filter used to enforce the <code>excludeFiles</code> config
   * parameter.
   * 
   * @return
   */
  private FilenameFilter getFileFilter() {
    if (excludeFiles == null) {
      excludeFiles = ".*\\.svn|.*\\.git|\\._.*|\\.DS_Store|thumbs\\.db|lockwait\\.dat";
    }
    return new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return !name.matches(excludeFiles);
      }
    };
  }

  /**
   * Given a hda, extract a result set from it.
   * 
   * @param manifestFile
   * @param rsName
   * @return
   * @throws MojoExecutionException
   */
  private DataResultSet getResultSetFromHda(File manifestFile, String rsName) throws MojoExecutionException {
    DataBinder manifest = getBinderFromHda(manifestFile);

    DataResultSet manifestRs = manifest.getResultSet(rsName);

    if (manifestRs == null) {
      throw new MojoExecutionException("Resultset " + rsName + " doesn't exist in file " + manifestFile);
    }
    return manifestRs;
  }

  /**
   * Unserialize a hda file into a binder.
   * 
   * @param manifestFile
   * @return
   * @throws MojoExecutionException
   */
  private DataBinder getBinderFromHda(File manifestFile) throws MojoExecutionException {
    if (manifestFile == null || !manifestFile.exists()) {
      throw new MojoExecutionException("File " + manifestFile + " does not exist");
    }

    // TODO: fix hard coded encoding
    HdaBinderSerializer serializer = new HdaBinderSerializer("UTF-8", new DataFactoryImpl());
    DataBinder binder = null;

    try {
      binder = serializer.parseBinder(new FileReader(manifestFile));

    } catch (Exception e) {
      throw new MojoExecutionException("Error opening" + manifestFile, e);
    }

    return binder;
  }

  /**
   * Adds a manifest listing to the zip listing, if the listing is a component,
   * read the component's .hda and add any component specific resources.
   * 
   * @param zipListing
   * @param manifestEntry
   * @throws MojoExecutionException
   */
  private void addToZipList(Map<String, String> zipListing, DataObject manifestEntry) throws MojoExecutionException {
    String entryType = manifestEntry.get("entryType");
    String location = manifestEntry.get("location");

    // remove component dir prefix
    if (location.startsWith(componentName)) {
      location = location.replaceFirst(componentName + "/", "");
    }
    String sourcePathFile="";
    if (!"".equals(componentFolder) && null != componentFolder){
        sourcePathFile=new File(componentFolder,location).toString();
    }
    zipListing.put(sourcePathFile, "component/" + componentName + "/" + location);

    if (entryType.equals("component")) {

      File componentHdaFile = new File(sourcePathFile);

      addComponentResourcesToZipList(zipListing, componentHdaFile);
    }
  }

  /**
   * Adds all files needed within a component to the zip listing.
   * 
   * @param zipListing
   * @param componentHdaFile
   * @throws MojoExecutionException
   */
  private void addComponentResourcesToZipList(Map<String, String> zipListing, File componentHdaFile)
      throws MojoExecutionException {
    String componentName = componentHdaFile.getName().replaceAll(".hda", "");

    // if component name not set yet, set it.
    if (this.componentName == null) {
      this.componentName = componentName;
    }

    String baseZipPath = "component/" + componentName + "/";

    // read ResourceDefinition from hda file.
    DataResultSet componentResources = getResultSetFromHda(componentHdaFile, "ResourceDefinition");

    for (DataObject resourceRow : componentResources.getRows()) {
      String type = resourceRow.get("type");
      String fileName = resourceRow.get("filename");
      String sourcePathFile="";
        if (!"".equals(componentFolder) && null != componentFolder){
            sourcePathFile=new File(componentFolder,fileName).toString();
        }
      // template entries have multiple files so they need to be included by
      // folder.
      if (type != null && type.equals("template")) {
        String templateFolder = new File(sourcePathFile).getParent();
        zipListing.put(templateFolder, baseZipPath + fileName);
      //Check different contexts for cfg files
      }else if(type != null && type.equals("environment")){
        zipListing.put(getEnvironmentFilePath(componentFolder, fileName, environment),baseZipPath + fileName);
      }else{
          zipListing.put(sourcePathFile, baseZipPath + fileName);
      }
    }
  }

    private String getEnvironmentFilePath(String basefolder, String filename,String context){
        if (null!=context && !"".equals(context)){
            File contextFile=new File(basefolder,filename+"."+context);
            if (contextFile.exists()){
                getLog().info("Using context file : "+contextFile.toString());
                return contextFile.toString();
            }else{
                getLog().warn("Context file not found :"+contextFile);
            }
        }
        getLog().info( "Using default file: "+new File(basefolder,filename).toString());
        return new File(basefolder,filename).toString();
    }
}
