package gui;

import gui.webdiff.WebDiff;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitService;
import org.refactoringminer.astDiff.models.ProjectASTDiff;
import org.refactoringminer.astDiff.utils.URLHelper;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

public class RunWithLocallyClonedRepository {
    public static void main(String[] args) throws Exception {
        String url = "https://github.com/apache/commons-io/commit/7566f557c2fa172d7677fcde06514e8a68356f81";
        String repo = URLHelper.getRepo(url);
        String commit = URLHelper.getCommit(url);

        GitService gitService = new GitServiceImpl();
        String projectName = repo.substring(repo.lastIndexOf("/") + 1, repo.length() - 4);
        String pathToClonedRepository = "tmp/" + projectName;
        Repository repository = gitService.cloneIfNotExists(pathToClonedRepository, repo);

        ProjectASTDiff projectASTDiff = new GitHistoryRefactoringMinerImpl().diffAtCommit(repository, commit);
        new WebDiff(projectASTDiff).run();
    }
}
