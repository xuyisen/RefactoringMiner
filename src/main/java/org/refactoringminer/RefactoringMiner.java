package org.refactoringminer;

import analysis.RefactoringAnalysis;
import analysis.entity.Commit;
import analysis.entity.CommitsResponse;
import analysis.entity.RefactoringForAnalysis;
import analysis.utils.JsonUtil;
import com.github.gumtreediff.matchers.Mapping;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gr.uom.java.xmi.diff.UMLModelDiff;
import gui.webdiff.WebDiffRunnerCli;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.*;
import org.refactoringminer.astDiff.models.ASTDiff;
import org.refactoringminer.astDiff.models.ExtendedMultiMappingStore;
import org.refactoringminer.astDiff.models.ProjectASTDiff;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;
import spark.utils.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;


public class RefactoringMiner {
	public static Path path = null;
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			throw argumentException();
		}

		final String option = args[0];
		if (option.equalsIgnoreCase("-h") || option.equalsIgnoreCase("--h") || option.equalsIgnoreCase("-help")
				|| option.equalsIgnoreCase("--help")) {
			printTips();
			return;
		}

		if (option.equalsIgnoreCase("-a")) {
			detectAll(args);
		} else if (option.equalsIgnoreCase("-bc")) {
			detectBetweenCommits(args);
		} else if (option.equalsIgnoreCase("-bt")) {
			detectBetweenTags(args);
		} else if (option.equalsIgnoreCase("-c")) {
			detectAtCommit(args);
		} else if (option.equalsIgnoreCase("-gc")) {
			detectAtGitHubCommit(args);
		} else if (option.equalsIgnoreCase("-gp")) {
			detectAtGitHubPullRequest(args);
		} else if (option.equalsIgnoreCase("diff")) {
			new WebDiffRunnerCli().execute(Arrays.copyOfRange(args, 1, args.length));
		}else if(option.equalsIgnoreCase("-p")){
			detectPure(args);
		}else if(option.equalsIgnoreCase("-pc")) {
			detectPureWithContext(args);
		}else if(option.equalsIgnoreCase("-pbc")) {
			detectPureWithContextBetweenCommits(args);
		} else if(option.equalsIgnoreCase("-scr")){
			detectRefactoringBySourceCode(args);
		} else if (option.equalsIgnoreCase("-spr")){
			detectPullPushRefactoringBySourceCode(args);
		}else if(option.equalsIgnoreCase("-scp")){
			detectPureBySourceCode(args);
		} else if(option.equalsIgnoreCase("-ast")){
			detectAST(args);
		} else {
			throw argumentException();
		}
	}

	public static void detectAST(String[] args) throws Exception{
		String filePath1 = args[1];
		String filePath2 = args[2];
		GitHistoryRefactoringMiner refactoringMiner = new GitHistoryRefactoringMinerImpl();
		File file1 = new File(filePath1);
		File file2 = new File(filePath2);
		ProjectASTDiff projectASTDiff = refactoringMiner.diffAtDirectories(file1, file2);
		double accuracy = 0.0;
		int tp = 0;
		int all = 0;
		for (ASTDiff astDiff : projectASTDiff.getDiffSet()) {
			ExtendedMultiMappingStore allMappings = astDiff.getAllMappings();
			for (Mapping mapping : allMappings.getMappings()) {
				if (mapping.first.getLabel().equals(mapping.second.getLabel())) {
					tp++;
				}
				all++;
			}
		}
		accuracy = (double) tp / all;
		System.out.println("Accuracy: " + accuracy);
	}

	private static void detectPullPushRefactoringBySourceCode(String[] args) throws Exception{
		String superclassFilePath = args[1];
		String superclassRefactoredFilePath = args[2];
		String subclassFilePath = args[3];
		String subclassRefactoredFilePath = args[4];
		String refactoringType = args[5];
		String subclassSourceCode = new String(Files.readAllBytes(Paths.get(subclassFilePath)));
		String subclassRefactoredSourceCode = new String(Files.readAllBytes(Paths.get(subclassRefactoredFilePath)));
		String superclassSourceCode = new String(Files.readAllBytes(Paths.get(superclassFilePath)));
		String superclassRefactoredSourceCode = new String(Files.readAllBytes(Paths.get(superclassRefactoredFilePath)));
		GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
		Map<String, String> fileContentsBefore = new HashMap<>();
		Map<String, String> fileContentsCurrent = new HashMap<>();
		fileContentsBefore.put(subclassFilePath, subclassSourceCode);
		fileContentsBefore.put(superclassFilePath, superclassSourceCode);
		fileContentsCurrent.put(subclassFilePath, subclassRefactoredSourceCode);
		fileContentsCurrent.put(superclassFilePath, superclassRefactoredSourceCode);
		final boolean[] detectResult = {false, false};
		detector.detectAtFileContents(fileContentsBefore, fileContentsCurrent, new RefactoringHandler() {
			@Override
			public void handle(String commitId, List<Refactoring> refactorings) {
				if(!refactorings.isEmpty()) {
					for(Refactoring refactoring : refactorings) {
						if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), refactoringType)) {
							detectResult[0] = true;
							return;
						}
					}

				}
			}

			@Override
			public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
				System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
						commitsCount, errorCommitsCount, refactoringsCount));
			}

			@Override
			public void handleException(String commit, Exception e) {
				System.err.println("Error processing commit " + commit);
				e.printStackTrace(System.err);
			}

			@Override
			public void handleModelDiffWithContent(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
				detectResult[1] = true;
				if(CollectionUtils.isEmpty(refactorings)) {
					detectResult[1] = false;
					return;
				}
				boolean isPure = false;
				for (Refactoring refactoring : refactorings) {
					if(!isForRefactoringType(refactoring)) {
						continue;
					}
					PurityCheckResult purityCheckResult = PurityChecker.check(refactoring, refactorings, modelDiff);
					if(purityCheckResult != null && purityCheckResult.isPure()) {
						isPure = true;
					}
				}
				detectResult[1] = isPure;
			}
		});
		System.out.println(detectResult[0] + " " + detectResult[1]);
	}

	private static void detectRefactoringBySourceCode(String[] args) throws Exception{
		String fileName = args[1];
        String sourceCodeBeforeFile = args[2];
		String sourceCodeAfterFile = args[3];
		String refactoringType = args[4];
		String sourceCodeBefore = new String(Files.readAllBytes(Paths.get(sourceCodeBeforeFile)));
		String sourceCodeAfter = new String(Files.readAllBytes(Paths.get(sourceCodeAfterFile)));
		GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
		Map<String, String> fileContentsBefore = new HashMap<>();
		Map<String, String> fileContentsCurrent = new HashMap<>();
		fileContentsBefore.put(fileName, sourceCodeBefore);
		fileContentsCurrent.put(fileName, sourceCodeAfter);
		final boolean[] detectResult = {false, false};
		detector.detectAtFileContents(fileContentsBefore, fileContentsCurrent, new RefactoringHandler() {
			@Override
			public void handle(String commitId, List<Refactoring> refactorings) {
				if(!refactorings.isEmpty()) {
					for(Refactoring refactoring : refactorings) {
						if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), refactoringType)) {
							detectResult[0] = true;
							return;
						}
					}

				}
			}

			@Override
			public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
				System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
						commitsCount, errorCommitsCount, refactoringsCount));
			}

			@Override
			public void handleException(String commit, Exception e) {
				System.err.println("Error processing commit " + commit);
				e.printStackTrace(System.err);
			}

			@Override
			public void handleModelDiffWithContent(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
				detectResult[1] = true;
				if(CollectionUtils.isEmpty(refactorings)) {
					detectResult[1] = false;
					return;
				}
				boolean isPure = false;
				for (Refactoring refactoring : refactorings) {
					if(!isForRefactoringType(refactoring)) {
						continue;
					}
					PurityCheckResult purityCheckResult = PurityChecker.check(refactoring, refactorings, modelDiff);
					if(purityCheckResult != null && purityCheckResult.isPure()) {
						isPure = true;
					}
				}
				detectResult[1] = isPure;
			}
		});
		System.out.println(detectResult[0] + " " + detectResult[1]);
	}

	private static boolean isForRefactoringType(Refactoring refactoring){
		if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Extract Method")){
			return true;
		}else if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Extract and Move Method")){
			return true;
		}else if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Inline Method")){
			return true;
		}else if (StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Move and Inline Method")){
			return true;
		}else if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Move Method")) {
			return true;
		}else if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Move and Rename Method")) {
			return true;
		}else if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Pull Up Method")) {
			return true;
		}else if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), "Push Down Method")) {
			return true;
		}
		return false;
	}
	private static void detectPureBySourceCode(String[] args) throws Exception{
		String fileName = args[1];
		String sourceCodeBefore = args[2];
		String sourceCodeAfter = args[3];
		GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
		Map<String, String> fileContentsBefore = new HashMap<>();
		Map<String, String> fileContentsCurrent = new HashMap<>();
		fileContentsBefore.put(fileName, sourceCodeBefore);
		fileContentsCurrent.put(fileName, sourceCodeAfter);
		final boolean[] hasRefactoring = {false};

	}

	public static void detectPure(String[] args) throws Exception{
		int maxArgLength = processJSONoption(args, 3);
		if (args.length > maxArgLength) {
			throw argumentException();
		}
		String folder = args[1];
		String branch = null;
		if (containsBranchArgument(args)) {
			branch = args[2];
		}
		GitService gitService = new GitServiceImpl();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			startJSON();
			detector.detectAll(repo, branch, new RefactoringHandler() {
				private int commitCount = 0;
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {

				}

				@Override
				public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
					System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
							commitsCount, errorCommitsCount, refactoringsCount));
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}

				@Override
				public void handleModelDiffWithContent(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
					if(commitCount > 0) {
						betweenCommitsJSON();
					}
					commitJSONForPure(gitURL, commitId, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent);
					commitCount++;
				}
			});
			endJSON();
		}
	}
	public static void detectPureWithContextBetweenCommits(String[] args) throws Exception{
		int maxArgLength = processJSONoption(args, 5);
		if (!(args.length == maxArgLength-1 || args.length == maxArgLength)) {
			throw argumentException();
		}
		String folder = args[1];
		String startCommit = args[2];
		String endCommit = args[3];
		String skipFilePath = args[4];
		String skipCommitsStr = new String(Files.readAllBytes(Paths.get(skipFilePath)));
		List<String> skipCommits = Arrays.asList(skipCommitsStr.split("\n"));
		String projectName = folder.substring(folder.lastIndexOf("/") + 1);
		GitService gitService = new GitServiceImpl();
		List<Commit> commitsForRAG = new ArrayList<>();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			detector.detectBetweenCommits(repo, startCommit, endCommit, new RefactoringHandler() {

				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {

				}

				@Override
				public boolean skipCommit(String commitId) {
					if(skipCommits.contains(commitId)){
						return true;
					}
					return false;
				}



				@Override
				public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
					System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
							commitsCount, errorCommitsCount, refactoringsCount));
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}

				@Override
				public void handleModelDiffWithContent(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
					Commit commit = new Commit();
					List<RefactoringForAnalysis> refactoringForAnalysisList = RefactoringAnalysis.analysisRefactorings(commitId, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent);
					if(!refactoringForAnalysisList.isEmpty()){
						commit.setRefactorings(refactoringForAnalysisList);
					}else{
						commit.setRefactorings(new ArrayList<>());
					}
					commit.setUrl(gitURL);
					commit.setCommitId(commitId);
					commitsForRAG.add(commit);
				}
			});
			String refactoringJsonPathWithSC = "tmp/data/output/" + projectName +"_em_pure_refactoring_w_sc_v6.json";
			CommitsResponse commitsResponse = new CommitsResponse();
			commitsResponse.setCommits(commitsForRAG);
			JsonUtil.writeJsonToFile(refactoringJsonPathWithSC, commitsResponse);
		}
	}

	public static void detectPureWithContext(String[] args) throws Exception{
		int maxArgLength = processJSONoption(args, 3);
		if (args.length > maxArgLength) {
			throw argumentException();
		}
		String folder = args[1];
		String branch = null;
		if (containsBranchArgument(args)) {
			branch = args[2];
		}
		String projectName = folder.substring(folder.lastIndexOf("/") + 1);
		GitService gitService = new GitServiceImpl();
		List<Commit> commitsForRAG = new ArrayList<>();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			String branchForRecord = branch;
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			detector.detectAll(repo, branch, new RefactoringHandler() {
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {

				}

				@Override
				public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
					System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
							commitsCount, errorCommitsCount, refactoringsCount));
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}

				@Override
				public void handleModelDiffWithContent(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
					Commit commit = new Commit();
					List<RefactoringForAnalysis> refactoringForAnalysisList = RefactoringAnalysis.analysisRefactorings(commitId, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent);
					if(!refactoringForAnalysisList.isEmpty()){
						commit.setRefactorings(refactoringForAnalysisList);
					}else{
						commit.setRefactorings(new ArrayList<>());
					}
					commit.setUrl(gitURL);
					commit.setCommitId(commitId);
					commit.setBranch(branchForRecord);
					commitsForRAG.add(commit);
				}
			});
		}
		String refactoringJsonPathWithSC = "tmp/data/output/" + projectName +"_em_pure_refactoring_w_sc_v6.json";
		CommitsResponse commitsResponse = new CommitsResponse();
		commitsResponse.setCommits(commitsForRAG);
		JsonUtil.writeJsonToFile(refactoringJsonPathWithSC, commitsResponse);
	}

	public static void detectAll(String[] args) throws Exception {
		int maxArgLength = processJSONoption(args, 3);
		if (args.length > maxArgLength) {
			throw argumentException();
		}
		String folder = args[1];
		String branch = null;
		if (containsBranchArgument(args)) {
			branch = args[2];
		}
		GitService gitService = new GitServiceImpl();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			startJSON();
			detector.detectAll(repo, branch, new RefactoringHandler() {
				private int commitCount = 0;
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {
					if(commitCount > 0) {
						betweenCommitsJSON();
					}
					commitJSON(gitURL, commitId, refactorings);
					commitCount++;
				}

				@Override
				public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
					System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
							commitsCount, errorCommitsCount, refactoringsCount));
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}

				@Override
				public void handleModelDiffWithContent(String commitId, List<Refactoring> refactoringsAtRevision, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {

				}
			});
			endJSON();
		}
	}

	private static boolean containsBranchArgument(String[] args) {
		return args.length == 3 || (args.length > 3 && args[3].equalsIgnoreCase("-json"));
	}

	public static void detectBetweenCommits(String[] args) throws Exception {
		int maxArgLength = processJSONoption(args, 4);
		if (!(args.length == maxArgLength-1 || args.length == maxArgLength)) {
			throw argumentException();
		}
		String folder = args[1];
		String startCommit = args[2];
		String endCommit = containsEndArgument(args) ? args[3] : null;
		GitService gitService = new GitServiceImpl();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			startJSON();
			detector.detectBetweenCommits(repo, startCommit, endCommit, new RefactoringHandler() {
				private int commitCount = 0;
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {
					if(commitCount > 0) {
						betweenCommitsJSON();
					}
					commitJSON(gitURL, commitId, refactorings);
					commitCount++;
				}

				@Override
				public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
					System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
							commitsCount, errorCommitsCount, refactoringsCount));
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}
			});
			endJSON();
		}
	}

	public static void detectBetweenTags(String[] args) throws Exception {
		int maxArgLength = processJSONoption(args, 4);
		if (!(args.length == maxArgLength-1 || args.length == maxArgLength)) {
			throw argumentException();
		}
		String folder = args[1];
		String startTag = args[2];
		String endTag = containsEndArgument(args) ? args[3] : null;
		GitService gitService = new GitServiceImpl();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			startJSON();
			detector.detectBetweenTags(repo, startTag, endTag, new RefactoringHandler() {
				private int commitCount = 0;
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {
					if(commitCount > 0) {
						betweenCommitsJSON();
					}
					commitJSON(gitURL, commitId, refactorings);
					commitCount++;
				}

				@Override
				public void onFinish(int refactoringsCount, int commitsCount, int errorCommitsCount) {
					System.out.println(String.format("Total count: [Commits: %d, Errors: %d, Refactorings: %d]",
							commitsCount, errorCommitsCount, refactoringsCount));
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}
			});
			endJSON();
		}
	}

	private static boolean containsEndArgument(String[] args) {
		return args.length == 4 || (args.length > 4 && args[4].equalsIgnoreCase("-json"));
	}

	public static void detectAtCommit(String[] args) throws Exception {
		int maxArgLength = processJSONoption(args, 3);
		if (args.length != maxArgLength) {
			throw argumentException();
		}
		String folder = args[1];
		String commitId = args[2];
		GitService gitService = new GitServiceImpl();
		try (Repository repo = gitService.openRepository(folder)) {
			String gitURL = repo.getConfig().getString("remote", "origin", "url");
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			startJSON();
			detector.detectAtCommit(repo, commitId, new RefactoringHandler() {
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {
					commitJSON(gitURL, commitId, refactorings);
				}

				@Override
				public void handleException(String commit, Exception e) {
					System.err.println("Error processing commit " + commit);
					e.printStackTrace(System.err);
				}
			});
			endJSON();
		}
	}

	public static void detectAtGitHubCommit(String[] args) throws Exception {
		int maxArgLength = processJSONoption(args, 4);
		if (args.length != maxArgLength) {
			throw argumentException();
		}
		String gitURL = args[1];
		String commitId = args[2];
		int timeout = Integer.parseInt(args[3]);
		GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
		startJSON();
		detector.detectAtCommit(gitURL, commitId, new RefactoringHandler() {
			@Override
			public void handle(String commitId, List<Refactoring> refactorings) {
				Comparator<Refactoring> comparator = (Refactoring r1, Refactoring r2) -> r1.toString().compareTo(r2.toString());
				Collections.sort(refactorings, comparator);
				commitJSON(gitURL, commitId, refactorings);
			}

			@Override
			public void handleException(String commit, Exception e) {
				System.err.println("Error processing commit " + commit);
				e.printStackTrace(System.err);
			}
		}, timeout);
		endJSON();
	}

	public static void detectAtGitHubPullRequest(String[] args) throws Exception {
		int maxArgLength = processJSONoption(args, 4);
		if (args.length != maxArgLength) {
			throw argumentException();
		}
		String gitURL = args[1];
		int pullId = Integer.parseInt(args[2]);
		int timeout = Integer.parseInt(args[3]);
		GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
		startJSON();
		detector.detectAtPullRequest(gitURL, pullId, new RefactoringHandler() {
			private int commitCount = 0;
			@Override
			public void handle(String commitId, List<Refactoring> refactorings) {
				Comparator<Refactoring> comparator = (Refactoring r1, Refactoring r2) -> r1.toString().compareTo(r2.toString());
				Collections.sort(refactorings, comparator);
				if(commitCount > 0) {
					betweenCommitsJSON();
				}
				commitJSON(gitURL, commitId, refactorings);
				commitCount++;
			}

			@Override
			public void handleException(String commit, Exception e) {
				System.err.println("Error processing commit " + commit);
				e.printStackTrace(System.err);
			}
		}, timeout);
		endJSON();
	}

	private static int processJSONoption(String[] args, int maxArgLength) {
		if (args[args.length-2].equalsIgnoreCase("-json")) {
			path = Paths.get(args[args.length-1]);
			try {
				if(Files.exists(path)) {
					Files.delete(path);
				}
				if(Files.notExists(path)) {
					Files.createFile(path);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			maxArgLength = maxArgLength + 2;
		}
		return maxArgLength;
	}

	private static void commitJSON(String cloneURL, String currentCommitId, List<Refactoring> refactoringsAtRevision) {
		if(path != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("{").append("\n");
			sb.append("\t").append("\"").append("repository").append("\"").append(": ").append("\"").append(cloneURL).append("\"").append(",").append("\n");
			sb.append("\t").append("\"").append("sha1").append("\"").append(": ").append("\"").append(currentCommitId).append("\"").append(",").append("\n");
			String url = GitHistoryRefactoringMinerImpl.extractCommitURL(cloneURL, currentCommitId);
			sb.append("\t").append("\"").append("url").append("\"").append(": ").append("\"").append(url).append("\"").append(",").append("\n");
			sb.append("\t").append("\"").append("refactorings").append("\"").append(": ");
			sb.append("[");
			int counter = 0;
			for(Refactoring refactoring : refactoringsAtRevision) {
				sb.append(refactoring.toJSON());
				if(counter < refactoringsAtRevision.size()-1) {
					sb.append(",");
				}
				sb.append("\n");
				counter++;
			}
			sb.append("]").append("\n");
			sb.append("}");
			try {
				Files.write(path, sb.toString().getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void commitJSONForPure(String cloneURL, String currentCommitId, List<Refactoring> refactoringsAtRevision, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent){
		if(path != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("{").append("\n");
			sb.append("\t").append("\"").append("repository").append("\"").append(": ").append("\"").append(cloneURL).append("\"").append(",").append("\n");
			sb.append("\t").append("\"").append("sha1").append("\"").append(": ").append("\"").append(currentCommitId).append("\"").append(",").append("\n");
			String url = GitHistoryRefactoringMinerImpl.extractCommitURL(cloneURL, currentCommitId);
			sb.append("\t").append("\"").append("url").append("\"").append(": ").append("\"").append(url).append("\"").append(",").append("\n");
			sb.append("\t").append("\"").append("refactorings").append("\"").append(": ");
			sb.append("[");
			int counter = 0;
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			for(Refactoring refactoring : refactoringsAtRevision) {
				PurityCheckResult purityCheckResult = PurityChecker.check(refactoring, refactoringsAtRevision, modelDiff);
				JsonObject jsonObject = JsonParser.parseString(refactoring.toJSON()).getAsJsonObject();
				if(purityCheckResult != null) {
					JsonObject purityObject = gson.toJsonTree(purityCheckResult).getAsJsonObject();
					jsonObject.add("purity", purityObject);
				}else{
					jsonObject.addProperty("purity", "-");
				}
				sb.append(gson.toJson(jsonObject));
				if(counter < refactoringsAtRevision.size()-1) {
					sb.append(",");
				}
				sb.append("\n");
				counter++;
			}
			sb.append("]").append("\n");
			sb.append("}");
			try {
				Files.write(path, sb.toString().getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	public static void betweenCommitsJSON() {
		if(path != null) {
			StringBuilder sb = new StringBuilder();
			sb.append(",").append("\n");
			try {
				Files.write(path, sb.toString().getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void startJSON() {
		if(path != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("{").append("\n");
			sb.append("\"").append("commits").append("\"").append(": ");
			sb.append("[").append("\n");
			try {
				Files.write(path, sb.toString().getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void endJSON() {
		if(path != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("]").append("\n");
			sb.append("}");
			try {
				Files.write(path, sb.toString().getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void printTips() {
		System.out.println("-h\t\t\t\t\t\t\t\t\t\t\tShow options");
		System.out.println(
				"-a <git-repo-folder> <branch> -json <path-to-json-file>\t\t\t\t\tDetect all refactorings at <branch> for <git-repo-folder>. If <branch> is not specified, commits from all branches are analyzed.");
		System.out.println(
				"-bc <git-repo-folder> <start-commit-sha1> <end-commit-sha1> -json <path-to-json-file>\tDetect refactorings between <start-commit-sha1> and <end-commit-sha1> for project <git-repo-folder>");
		System.out.println(
				"-bt <git-repo-folder> <start-tag> <end-tag> -json <path-to-json-file>\t\t\tDetect refactorings between <start-tag> and <end-tag> for project <git-repo-folder>");
		System.out.println(
				"-c <git-repo-folder> <commit-sha1> -json <path-to-json-file>\t\t\t\tDetect refactorings at specified commit <commit-sha1> for project <git-repo-folder>");
		System.out.println(
				"-gc <git-URL> <commit-sha1> <timeout> -json <path-to-json-file>\t\t\t\tDetect refactorings at specified commit <commit-sha1> for project <git-URL> within the given <timeout> in seconds. All required information is obtained directly from GitHub using the OAuth token in github-oauth.properties");
		System.out.println(
				"-gp <git-URL> <pull-request> <timeout> -json <path-to-json-file>\t\t\tDetect refactorings at specified pull request <pull-request> for project <git-URL> within the given <timeout> in seconds for each commit in the pull request. All required information is obtained directly from GitHub using the OAuth token in github-oauth.properties");
	}

	private static IllegalArgumentException argumentException() {
		return new IllegalArgumentException("Type `RefactoringMiner -h` to show usage.");
	}
}
