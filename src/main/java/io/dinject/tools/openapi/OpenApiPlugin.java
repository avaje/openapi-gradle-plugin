package io.dinject.tools.openapi;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Plugin that moves the generated meta/openapi.json file into src/main/resources.
 */
public class OpenApiPlugin implements Plugin<Project> {

  /**
   * The location of the generated openapi file.
   */
  private static final String META_OPENAPI_JSON = "meta/openapi.json";

  @Override
  public void apply(Project project) {

    final OpenApiExtension params = project.getExtensions().create("openapi", OpenApiExtension.class);


    final Task test = project.getTasks().getByName("test");
    test.doFirst(new LocalTask(project, params));
  }

  class LocalTask implements Action<Task> {

    private final OpenApiExtension params;

    private final Logger logger;

    private final Project project;

    LocalTask(Project project, OpenApiExtension params) {
      this.params = params;
      this.project = project;
      this.logger = project.getLogger();
    }

    @Override
    public void execute(Task task) {

      final JavaPluginConvention plugin = project.getConvention().getPlugin(JavaPluginConvention.class);
      final SourceSet mainSourceSet = plugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

      final File resourceDir = findResourceDir(mainSourceSet);
      if (resourceDir == null) {
        logger.warn("Unable to find src/main/resources directory");
        return;
      }

      final File metaOpenApi = findMetaOpenApi(mainSourceSet);
      if (metaOpenApi == null) {
        logger.warn("Unable to find the generated openapi.json file");
        return;
      }

      File destFile = new File(resourceDir, params.destination);
      File destDir = destFile.getParentFile();
      if (!destDir.exists() && !destDir.mkdirs()) {
        logger.error("Failed to make resource directory " + destDir);
      } else {
        moveOpenApiFile(metaOpenApi, destFile);
      }
    }

    private void moveOpenApiFile(File metaOpenApi, File destFile) {
      try {
        Files.copy(metaOpenApi.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logger.info("moved open api file to main/resources: " + params.destination);
        if (!metaOpenApi.delete()) {
          logger.warn("unable to delete " + metaOpenApi);
        }
      } catch (IOException e) {
        logger.error("Error copying openapi file", e);
      }
    }

    /**
     * Find the generated meta/openapi.json file.
     */
    private File findMetaOpenApi(SourceSet mainSourceSet) {
      final FileCollection classesDirs = mainSourceSet.getOutput().getClassesDirs();
      final Set<File> dirs = classesDirs.getFiles();
      for (File dir : dirs) {
        File metaApi = new File(dir, META_OPENAPI_JSON);
        if (metaApi.exists()) {
          return metaApi;
        }
      }
      return null;
    }

    /**
     * Return the src/main/resources directory.
     */
    private File findResourceDir(SourceSet mainSourceSet) {

      final Set<File> resDirs = mainSourceSet.getResources().getSrcDirs();
      for (File dir : resDirs) {
        if (dir != null) {
          // just returning the first
          return dir;
        }
      }
      return null;
    }
  }
}
