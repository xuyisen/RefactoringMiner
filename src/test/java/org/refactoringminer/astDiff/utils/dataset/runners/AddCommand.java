package org.refactoringminer.astDiff.utils.dataset.runners;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.refactoringminer.astDiff.actions.ASTDiff;
import org.refactoringminer.astDiff.actions.ProjectASTDiff;
import org.refactoringminer.astDiff.utils.CaseInfo;
import org.refactoringminer.astDiff.utils.MappingExportModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import static org.refactoringminer.astDiff.utils.UtilMethods.getFinalFilePath;
import static org.refactoringminer.astDiff.utils.UtilMethods.getFinalFolderPath;

// Command class for the "add" command
@Parameters(commandDescription = "Add files to the index")
public class AddCommand extends BaseCommand {
    @Parameter(names = {"-f", "--files"},
            description = "Files to add",
            variableArity = true,
            required = false)
    private Set<String> files;

    @Override
    void postValidationExecution() {
        String infoFile = diffDataSet.resolve(problematic);
        String dir = diffDataSet.getDir();
        ProjectASTDiff projectASTDiff = diffDataSet.getProjectASTDiff(url, repo, commit);
        if (projectASTDiff == null) throw new IllegalArgumentException("Invalid URL or repository/commit pair");
        try {
            addTestCase(
                    repo,
                    commit,
                    projectASTDiff,
                    dir,
                    infoFile, files);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static void addTestCase(String repo, String commit, ProjectASTDiff projectASTDiff, String mappingsPath, String testInfoFile, Set<String> selected_files) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonFile = mappingsPath + testInfoFile;

        for (ASTDiff astDiff : projectASTDiff.getDiffSet()) {
            String finalPath = getFinalFilePath(astDiff, mappingsPath,  repo, commit);
            Files.createDirectories(Paths.get(getFinalFolderPath(mappingsPath,repo,commit)));
            MappingExportModel.exportToFile(new File(finalPath), astDiff.getAllMappings());
        }
        List<CaseInfo> infos = mapper.readValue(new File(jsonFile), new TypeReference<List<CaseInfo>>(){});
        CaseInfo caseInfo = new CaseInfo(repo,commit, selected_files);
        boolean goingToAdd = true;
        if (infos.contains(caseInfo))
        {
            System.err.println("Repo-Commit pair already exists in the data folder");
            System.err.println("Enter yes to confirm the overwrite");
            String input = new Scanner(System.in).next();
            if (!input.equals("yes")) goingToAdd = false;
        }
        else {
            infos.add(caseInfo);
        }
        if (goingToAdd) {
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(new File(jsonFile), infos);
                System.err.println("New case added/updated successfully");
                System.err.println("Repo=" + repo);
                System.err.println("Commit=" + commit);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            System.err.println("Nothing updated");
        }
    }
}