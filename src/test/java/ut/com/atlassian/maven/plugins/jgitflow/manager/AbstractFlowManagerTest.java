package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import com.google.common.base.Strings;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectSorter;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.PlexusJUnit4TestCase;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.junit.After;
import org.junit.Before;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @since version
 */
public abstract class AbstractFlowManagerTest extends PlexusJUnit4TestCase
{
    protected MavenProjectBuilder projectBuilder;

    protected ArtifactRepository localRepository;

    private static final DefaultContext EMPTY_CONTEXT = new DefaultContext()
    {
        public Object get( Object key ) throws ContextException
        {
            return null;
        }
    };

    @Before
    public void doSetup() throws Exception
    {
        projectBuilder = (MavenProjectBuilder) lookup(MavenProjectBuilder.ROLE);
        ArtifactRepositoryLayout layout = (ArtifactRepositoryLayout) lookup(ArtifactRepositoryLayout.ROLE, "default");
        String localRepoPath = getTestFile("target/local-repository").getAbsolutePath().replace('\\', '/');
        localRepository = new DefaultArtifactRepository("local", "file://" + localRepoPath, layout);
    }

    @After
    public void doTearDown() throws Exception
    {
        ((Contextualizable) projectBuilder).contextualize(EMPTY_CONTEXT);
        ((Contextualizable) lookup(WagonManager.ROLE)).contextualize(EMPTY_CONTEXT);
    }

    @Override
    protected InputStream getCustomConfiguration() throws Exception
    {
        return getClass().getResourceAsStream("/default-components.xml");
    }

    protected void basicReleaseRewriteTest(String projectName) throws Exception
    {
        basicReleaseRewriteTest(projectName,"");
    }

    protected void basicReleaseRewriteTest(String projectName, String releaseVersion) throws Exception
    {
        List<MavenProject> projects = createReactorProjects("rewrite-for-release",projectName);
        File projectRoot = projects.get(0).getBasedir();
        
        ReleaseContext ctx = new ReleaseContext(projectRoot);
        
        if(!Strings.isNullOrEmpty(releaseVersion))
        {
            ctx.setDefaultReleaseVersion(releaseVersion);
        }
        
        ctx.setInteractive(false);

        basicReleaseRewriteTest(projectName, ctx);
    }

    protected void basicReleaseRewriteTest(String projectName, ReleaseContext ctx) throws Exception
    {
        List<MavenProject> projects = createReactorProjects("rewrite-for-release",projectName);
        File projectRoot = ctx.getBaseDir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);

        assertOnDevelop(flow);

        initialCommitAll(flow);
        FlowReleaseManager relman = getReleaseManager();

        relman.start(ctx,projects);

        assertOnRelease(flow, ctx.getDefaultReleaseVersion());

        comparePomFiles(projects);
    }
    
    protected void initialCommitAll(JGitFlow flow) throws Exception
    {
        flow.git().add().addFilepattern(".").call();
        flow.git().commit().setMessage("initial commit").call();
    }
    
    protected void assertOnDevelop(JGitFlow flow) throws Exception
    {
        assertEquals(flow.getDevelopBranchName(), flow.git().getRepository().getBranch());    
    }

    protected void assertOnRelease(JGitFlow flow, String version) throws Exception
    {
        if(Strings.isNullOrEmpty(version))
        {
            assertTrue(flow.git().getRepository().getBranch().startsWith(flow.getReleaseBranchPrefix()));
        }
        else
        {
            assertEquals(flow.getReleaseBranchPrefix() + version, flow.git().getRepository().getBranch());
        }
    }

    protected void assertOnHotfix(JGitFlow flow, String version) throws Exception
    {
        assertEquals(flow.getHotfixBranchPrefix() + version, flow.git().getRepository().getBranch());
    }
    
    protected FlowReleaseManager getReleaseManager() throws Exception
    {
        return (FlowReleaseManager) lookup(FlowReleaseManager.class.getName());    
    }

    protected String readTestProjectFile(String fileName) throws IOException
    {
        return ReleaseUtil.readXmlFile(getTestFile("target/test-classes/projects/" + fileName));
    }
    
    protected List<MavenProject> createReactorProjects(String path, String subpath) throws Exception
    {
        return createReactorProjects(path, path, subpath, true);
    }

    protected List<MavenProject> createReactorProjectsNoClean(String path, String subpath) throws Exception
    {
        return createReactorProjects(path, path, subpath, false);
    }
    
    protected List<MavenProject> createReactorProjects( String path, String targetPath, String subpath, boolean clean )
            throws Exception
    {
        File testFile = getTestFile( "target/test-classes/projects/" + path + "/" + subpath + "/pom.xml" );
        Stack<File> projectFiles = new Stack<File>();
        projectFiles.push( testFile );

        List<DefaultArtifactRepository> repos =
                Collections.singletonList( new DefaultArtifactRepository( "central", getRemoteRepositoryURL(), new DefaultRepositoryLayout() ) );

        Repository repository = new Repository();
        repository.setId( "central" );
        repository.setUrl( getRemoteRepositoryURL() );

        ProfileManager profileManager = new DefaultProfileManager( getContainer() );
        Profile profile = new Profile();
        profile.setId( "profile" );
        profile.addRepository( repository );
        profileManager.addProfile( profile );
        profileManager.activateAsDefault( profile.getId() );

        List<MavenProject> reactorProjects = new ArrayList<MavenProject>();
        String cleaned = "";
        
        while ( !projectFiles.isEmpty() )
        {
            File file = (File) projectFiles.pop();
            
            // Recopy the test resources since they are modified in some tests
            
            //FileUtils.copyDirectory(srcDir,file.getParentFile());
            String filePath = file.getPath();
            int index = filePath.indexOf( "test-classes" ) + "test-classes".length() + 1;
            filePath = filePath.substring( index ).replace( '\\', '/' );

            File newFile = getTestFile( "target/test-classes/" + StringUtils.replace( filePath, path, targetPath ) );

            if(clean && !cleaned.equals(newFile.getParentFile().getName()))
            {
                //clean the parent dir
                FileUtils.cleanDirectory(newFile.getParentFile());
    
                File srcDir = new File(getTestFile( "src/test/resources/"),filePath).getParentFile();
                FileUtils.copyDirectoryStructure(srcDir, newFile.getParentFile());
                
                cleaned = newFile.getParentFile().getName();
            }

            MavenProject project = projectBuilder.build( newFile, localRepository, profileManager );

            for ( Iterator i = project.getModules().iterator(); i.hasNext(); )
            {
                String module = (String) i.next();

                projectFiles.push( new File( file.getParentFile(), module + "/pom.xml" ) );
            }

            reactorProjects.add( project );
        }

        ProjectSorter sorter = new ProjectSorter( reactorProjects );

        reactorProjects = sorter.getSortedProjects();

        ArtifactFactory artifactFactory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        ArtifactCollector artifactCollector = (ArtifactCollector) lookup( ArtifactCollector.class.getName() );
        ArtifactMetadataSource artifactMetadataSource = (ArtifactMetadataSource) lookup( ArtifactMetadataSource.ROLE );

        // pass back over and resolve dependencies - can't be done earlier as the order may not be correct
        for ( Iterator i = reactorProjects.iterator(); i.hasNext(); )
        {
            MavenProject project = (MavenProject) i.next();

            project.setRemoteArtifactRepositories( repos );
            project.setPluginArtifactRepositories( repos );

            Artifact projectArtifact = project.getArtifact();

            Map managedVersions = createManagedVersionMap(
                    ArtifactUtils.versionlessKey(projectArtifact.getGroupId(), projectArtifact.getArtifactId()),
                    project.getDependencyManagement(), artifactFactory );

            project.setDependencyArtifacts( project.createArtifacts( artifactFactory, null, null ) );

            ArtifactResolutionResult result = artifactCollector.collect( project.getDependencyArtifacts(),
                    projectArtifact, managedVersions,
                    localRepository, repos, artifactMetadataSource,
                    null, Collections.EMPTY_LIST );

            project.setArtifacts( result.getArtifacts() );
        }

        return reactorProjects;
    }

    private Map<String,Artifact> createManagedVersionMap(String projectId, DependencyManagement dependencyManagement, ArtifactFactory artifactFactory) throws ProjectBuildingException
    {
        Map<String,Artifact> map;
        if ( dependencyManagement != null && dependencyManagement.getDependencies() != null )
        {
            map = new HashMap<String,Artifact>();
            for ( Iterator i = dependencyManagement.getDependencies().iterator(); i.hasNext(); )
            {
                Dependency d = (Dependency) i.next();

                try
                {
                    VersionRange versionRange = VersionRange.createFromVersionSpec( d.getVersion() );
                    Artifact artifact = artifactFactory.createDependencyArtifact( d.getGroupId(), d.getArtifactId(),
                            versionRange, d.getType(),
                            d.getClassifier(), d.getScope() );
                    map.put( d.getManagementKey(), artifact );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( projectId, "Unable to parse version '" + d.getVersion() +
                            "' for dependency '" + d.getManagementKey() + "': " + e.getMessage(), e );
                }
            }
        }
        else
        {
            map = Collections.emptyMap();
        }
        return map;
    }

    private String getRemoteRepositoryURL() throws IOException
    {
        File testFile = getTestFile( "src/test/remote-repository" );
        if (testFile.getAbsolutePath().equals( testFile.getCanonicalPath() ) )
        {
            return "file://" + getTestFile( "src/test/remote-repository" ).getAbsolutePath().replace( '\\', '/' );
        }
        return "file://" + getTestFile( "src/test/remote-repository" ).getCanonicalPath().replace( '\\', '/' );
    }

    protected void comparePomFiles(List<MavenProject> reactorProjects)throws IOException
    {
        for (MavenProject project : reactorProjects)
        {
            comparePomFiles(project);
        }
    }

    protected void comparePomFiles(MavenProject project) throws IOException
    {
        File actualFile = project.getFile();
        File expectedFile = new File(actualFile.getParentFile(), "expected-pom.xml" );

        comparePomFiles(expectedFile, actualFile);
    }

    protected void comparePomFiles(File expectedFile, File actualFile) throws IOException
    {
        String expectedPom = ReleaseUtil.readXmlFile(expectedFile);
        String actualPom = ReleaseUtil.readXmlFile(actualFile);

        assertEquals(expectedPom,actualPom);
    }
}