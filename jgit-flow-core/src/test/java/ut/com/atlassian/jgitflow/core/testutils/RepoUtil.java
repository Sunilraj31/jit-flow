package ut.com.atlassian.jgitflow.core.testutils;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * @since version
 */
public class RepoUtil
{
    public static Git createEmptyRepository(File dir) throws GitAPIException
    {
        Git git = Git.init().setDirectory(dir).setBare(true).call();
        git.commit().setMessage("initial commit").call();

        return git;
    }

    public static Git createRepositoryWithMaster(File dir) throws GitAPIException
    {
        Git git = Git.init().setDirectory(dir).call();
        git.commit().setMessage("initial commit").call();

        return git;
    }

    public static Git createRepositoryWithMasterAndTag(File dir) throws GitAPIException
    {
        Git git = createRepositoryWithMaster(dir);
        git.tag().setName("1.0").setAnnotated(true).setMessage("tagging release 1.0").call();
        return git;
    }

    public static Git createRepositoryWithMasterAndDevelop(File dir) throws GitAPIException
    {
        Git git = Git.init().setDirectory(dir).call();
        git.commit().setMessage("initial commit").call();
        // TODO remove this method, replace with createRepositoryWithMaster

        return git;
    }

    public static Git createRepositoryWithBranches(File dir, String... branches) throws GitAPIException
    {
        Git git = Git.init().setDirectory(dir).call();
        git.commit().setMessage("initial commit").call();

        for (String branch : branches)
        {
            git.branchCreate().setName(branch).call();
            git.commit().setMessage("added branch " + branch).call();
        }

        return git;
    }
}
