package com.example.demo.services;

import com.example.demo.exceptions.IndexerException;
import org.opensolaris.opengrok.configuration.Configuration;
import org.opensolaris.opengrok.configuration.Project;
import org.opensolaris.opengrok.configuration.RuntimeEnvironment;
import org.opensolaris.opengrok.configuration.messages.Message;
import org.opensolaris.opengrok.history.Repository;
import org.opensolaris.opengrok.history.RepositoryFactory;
import org.opensolaris.opengrok.index.DefaultIndexChangedListener;
import org.opensolaris.opengrok.index.IndexChangedListener;
import org.opensolaris.opengrok.index.Indexer;
import org.opensolaris.opengrok.logger.LoggerFactory;
import org.opensolaris.opengrok.logger.LoggerUtil;
import org.opensolaris.opengrok.util.Executor;
import org.opensolaris.opengrok.util.OptionParser;
import org.opensolaris.opengrok.util.Statistics;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({"PMD.AvoidPrintStackTrace", "PMD.SystemPrintln"})
public final class AstIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AstIndexer.class);

    private static final String DEFAULT_SOURCE_ROOT = "/Users/bresai/opengrok/src/";
    private static final String DEFAULT_CONFIG = "/Users/bresai/opengrok/etc/configuration.xml";

    private static final AstIndexer astIndexer = new AstIndexer();
    private static final Indexer index = Indexer.getInstance();
    private static Configuration cfg = null;
    private static boolean listRepos = false;
    private static boolean runIndex = true;
    private static boolean optimizedChanged = false;
    private static boolean addProjects = false;
    private static boolean searchRepositories = false;
    private static boolean noindex = false;

    private static String configFilename = null;

    private static final ArrayList<String> repositories = new ArrayList<>();
    private static final HashSet<String> allowedSymlinks = new HashSet<>();
    private static final Set<String> defaultProjects = new TreeSet<>();
    private static final ArrayList<String> zapCache = new ArrayList<>();
    private static RuntimeEnvironment env = null;
    private static String host = null;
    private static int port = 0;

    public static OptionParser openGrok = null;

    public static AstIndexer getAstIndexer(){
        return astIndexer;
    }

    private static Indexer getRealIndex() {
        return index;
    }

    /**
     * Program entry point
     * @param source
     * @param config
     */
    @SuppressWarnings("PMD.UseStringBufferForStringAppends")
    public void indexer(String source, String data, String config) {
        Statistics stats = new Statistics(); //this won't count JVM creation though
        boolean update = true;

        Executor.registerErrorHandler();
        ArrayList<String> subFiles = new ArrayList<>();
        ArrayList<String> subFilesList = new ArrayList<>();

        boolean listFiles = false;
        boolean createDict = false;

        try {

            if (cfg == null) {
                cfg = new Configuration();
            }

            prepareConfigs(source, data, config);

            // Complete the configuration of repository types.
            List<Class<? extends Repository>> repositoryClasses
                    = RepositoryFactory.getRepositoryClasses();
            for (Class<? extends Repository> clazz : repositoryClasses) {
                // Set external repository binaries from System properties.
                try {
                    Field f = clazz.getDeclaredField("CMD_PROPERTY_KEY");
                    Object key = f.get(null);
                    if (key != null) {
                        cfg.setRepoCmd(clazz.getCanonicalName(),
                                System.getProperty(key.toString()));
                    }
                } catch (Exception e) {
                    // don't care
                }
            }

            // Logging starts here.
            if (cfg.isVerbose()) {
                String fn = LoggerUtil.getFileHandlerPattern();
                if (fn != null) {
                    System.out.println("Logging filehandler pattern: " + fn);
                }
            }

            // automatically allow symlinks that are directly in source root
            String file = cfg.getSourceRoot();
            if (file != null) {
                File sourceRootFile = new File(file);
                File[] projectDirs = sourceRootFile.listFiles();
                if (projectDirs != null) {
                    for (File projectDir : projectDirs) {
                        if (!projectDir.getCanonicalPath().equals(projectDir.getAbsolutePath())) {
                            allowedSymlinks.add(projectDir.getAbsolutePath());
                        }
                    }
                }
            }

            allowedSymlinks.addAll(cfg.getAllowedSymlinks());
            cfg.setAllowedSymlinks(allowedSymlinks);

            // If an user used customizations for projects he perhaps just
            // used the key value for project without a name but the code
            // expects a name for the project. Therefore we fill the name
            // according to the project key which is the same.
            for (Map.Entry<String, Project> entry : cfg.getProjects().entrySet()) {
                if (entry.getValue().getName() == null) {
                    entry.getValue().setName(entry.getKey());
                }
            }

            // Set updated configuration in RuntimeEnvironment.
            env.setConfiguration(cfg, subFilesList);

            // Let repository types to add items to ignoredNames.
            // This changes env so is called after the setConfiguration()
            // call above.
            RepositoryFactory.initializeIgnoredNames(env);

            if (noindex) {
                getRealIndex().sendToConfigHost(env, host, port);
                writeConfigToFile(env, configFilename);
                System.exit(0);
            }

            /*
             * Add paths to directories under source root. If projects
             * are enabled the path should correspond to a project because
             * project path is necessary to correctly set index directory
             * (otherwise the index files will end up in index data root
             * directory and not per project data root directory).
             * For the check we need to have 'env' already set.
             */
            for (String path : subFilesList) {
                String srcPath = env.getSourceRootPath();
                if (srcPath == null) {
                    System.err.println("Error getting source root from environment. Exiting.");
                    System.exit(1);
                }

                path = path.substring(srcPath.length());
                if (env.hasProjects()) {
                    // The paths need to correspond to a project.
                    if (Project.getProject(path) != null) {
                        subFiles.add(path);
                    } else {
                        System.err.println("The path " + path
                                + " does not correspond to a project");
                    }
                } else {
                    subFiles.add(path);
                }
            }

            if (!subFilesList.isEmpty() && subFiles.isEmpty()) {
                System.err.println("None of the paths were added, exiting");
                System.exit(1);
            }

            // If the webapp is running with a config that does not contain
            // 'projectsEnabled' property (case of upgrade or transition
            // from project-less config to one with projects), set the property
            // using a message so that the 'project/indexed' messages
            // emitted during indexing do not cause validation error.
            if (addProjects && host != null && port > 0) {
                Message m = Message.createMessage("config");
                m.addTag("set");
                m.setText("projectsEnabled = true");
                try {
                    m.write(host, port);
                } catch (ConnectException ce) {
                    LOGGER.log(Level.SEVERE, "Misconfig of webapp host or port", ce);
                    System.err.println("Couldn't notify the webapp (and host or port set): " + ce.getLocalizedMessage());
                }
            }

            // Get history first.
            getRealIndex().prepareIndexer(env, searchRepositories, addProjects,
                    defaultProjects,
                    listFiles, createDict, subFiles, repositories,
                    zapCache, listRepos);
            if (listRepos || !zapCache.isEmpty()) {
                return;
            }

            // And now index it all.
            if (runIndex || (optimizedChanged && env.isOptimizeDatabase())) {
                IndexChangedListener progress = new DefaultIndexChangedListener();
                getRealIndex().doIndexerExecution(update, subFiles, progress);
            }

            writeConfigToFile(env, configFilename);

            // Finally ping webapp to refresh indexes in the case of partial reindex
            // or send new configuration to the web application in the case of full reindex.
            if (host != null) {
                if (!subFiles.isEmpty()) {
                    getRealIndex().refreshSearcherManagers(env, subFiles, host, port);
                } else {
                    getRealIndex().sendToConfigHost(env, host, port);
                }
            }

        } catch (ParseException e) {
            System.err.println("** " + e.getMessage());
            System.exit(1);
        } catch (IndexerException ex) {
            LOGGER.log(Level.SEVERE, "Exception running indexer", ex);
            System.err.println(openGrok.getUsage());
            System.exit(1);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Unexpected Exception", e);
            System.err.println("Exception: " + e.getLocalizedMessage());
            System.exit(1);
        } finally {
            stats.report(LOGGER);
        }
    }

    private static void prepareConfigs(String source, String data, String config) throws Exception {
        addProjects = true;
        cfg.setProjectsEnabled(true);
        searchRepositories = true;
//            argv = parseOptions(argv);
        cfg.setVerbose(true);
        LoggerUtil.setBaseConsoleLogLevel(Level.INFO);
        configFilename = config;

        if (cfg == null) {
            cfg = new Configuration();
        }

        File sourceRoot = new File(source);
        if (!sourceRoot.isDirectory()) {
            throw new Exception("Source root " + sourceRoot + " must be a directory");
        }
        cfg.setSourceRoot(sourceRoot.getCanonicalPath());

        File dataRoot = new File(data);
        if (!dataRoot.exists() && !dataRoot.mkdirs()) {
            throw new Exception("Cannot create data root: " + dataRoot);
        }
        if (!dataRoot.isDirectory()) {
            throw new Exception("Data root must be a directory");
        }
        cfg.setDataRoot(dataRoot.getCanonicalPath());

        cfg.setHistoryEnabled(false);

        env = RuntimeEnvironment.getInstance();
    }

    /**
     * Write configuration to a file
     *
     * @param env      runtime environment
     * @param filename file name to write the configuration to
     * @throws IOException if I/O exception occurred
     */
    public static void writeConfigToFile(RuntimeEnvironment env, String filename) throws IOException {
        if (filename != null) {
            LOGGER.log(Level.INFO, "Writing configuration to {0}", filename);
            env.writeConfiguration(new File(filename));
            LOGGER.info("Done...");
        }
    }


    private AstIndexer() {
    }
}