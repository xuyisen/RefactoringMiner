package org.refactoringminer;

import analysis.RefactoringAnalysis;
import analysis.entity.Commit;
import analysis.entity.CommitsResponse;
import analysis.entity.RefactoringForAnalysis;
import analysis.utils.JsonUtil;
import com.github.gumtreediff.actions.model.Action;
import com.google.gson.*;
import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.diff.ExtractOperationRefactoring;
import gr.uom.java.xmi.diff.InlineOperationRefactoring;
import gr.uom.java.xmi.diff.UMLModelDiff;
import gui.webdiff.WebDiffRunnerCli;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.*;
import org.refactoringminer.astDiff.models.ASTDiff;
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

import static analysis.CallGraphExtractor.extractCallGraph;
import static analysis.CallGraphExtractor.queryCallGraph;


public class RefactoringMiner {
	public static Path path = null;
	public static void main(String[] args) throws Exception {
//		handleRefactoringResult(args);
//		detectASTForUrl(args);
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

		} else if (option.equalsIgnoreCase("-cg")){
			extractCallGraphForCommit(args);
		}
		else {
			throw argumentException();
		}
	}

	public static void extractCallGraphForCommit(String[] args) throws Exception {
		if (args.length != 6 && args.length != 8) {
			throw argumentException();
		}
		String projectName = args[1];
		String commitId = args[2];
		String filePath = args[3];
		String methodName = args[4];
		String lineNumber = args[5];
		String classesDirectory = args[6];
		String sootRoot = args[7];
		String commitPath = "tmp/data/"+ projectName + "/" + commitId + "_callGraph.json";
		if (Files.exists(Paths.get(commitPath))) {
			System.out.println(queryCallGraph(commitPath,
					filePath,
					methodName,
					Integer.parseInt(lineNumber)
			));
			return;
		}

		extractCallGraph(commitId,classesDirectory, sootRoot, commitPath);
		System.out.println(queryCallGraph(commitPath,
				filePath,
				methodName,
				Integer.parseInt(lineNumber)
		));
	}

	public static void detectAST(String codeBeforeFilePath, String humanRefactoringFile, String toolRefactoringFile, JsonObject jsonObject, String refactoringType) throws Exception{

		GitHistoryRefactoringMiner refactoringMiner = new GitHistoryRefactoringMinerImpl();
		File codeBeforeFile = new File(codeBeforeFilePath);
		List<Action> humanActions = new ArrayList<>();
		List<Action> toolActions = new ArrayList<>();
		if(StringUtils.equals(refactoringType, "Extract And Move Method")){
			String commitId = jsonObject.get("commitId").getAsString();
			String repo = "https://github.com/mockito/mockito.git";
			String filePathBefore = jsonObject.get("filePathBefore").getAsString();
			String description = jsonObject.get("description").getAsString();
			String[] targetClassNameList = description.split("\\.");
			String targetClassName = targetClassNameList[targetClassNameList.length - 1];
			ProjectASTDiff humanAstDiff = refactoringMiner.diffAtCommit(repo,
					commitId, 10);
			Set<ASTDiff> humanDiffs = humanAstDiff.getDiffSet();
			for (ASTDiff diff : humanDiffs) {
				if(diff.getSrcPath().equals(filePathBefore) || diff.getSrcPath().contains(targetClassName)){
					humanActions.addAll(diff.editScript.asList());
				}
			}
		}else{
			File humanRefactoring = new File(humanRefactoringFile);
			ProjectASTDiff humanAstDiff = refactoringMiner.diffAtDirectories(codeBeforeFile, humanRefactoring);
			if (humanAstDiff == null) {
				jsonObject.addProperty("astDiffPrecision", 0.0);
				jsonObject.addProperty("astDiffRecall", 0.0);
				jsonObject.addProperty("astDiffSimilarity", 0.0);
				return;
			}
			Set<ASTDiff> humanDiffs = humanAstDiff.getDiffSet();
			for (ASTDiff diff : humanDiffs) {
				humanActions.addAll(diff.editScript.asList());
			}
		}


		File toolRefactoring = new File(toolRefactoringFile);
		ProjectASTDiff toolAstDiff = refactoringMiner.diffAtDirectories(codeBeforeFile, toolRefactoring);
		if (toolAstDiff == null) {
			jsonObject.addProperty("astDiffPrecision", 0.0);
			jsonObject.addProperty("astDiffRecall", 0.0);
			jsonObject.addProperty("astDiffSimilarity", 0.0);
			return;
		}
		Set<ASTDiff> toolDiffs = toolAstDiff.getDiffSet();
		for (ASTDiff diff : toolDiffs) {
			toolActions.addAll(diff.editScript.asList());
		}
		if (humanActions.isEmpty() || toolActions.isEmpty()) {
			jsonObject.addProperty("astDiffPrecision", 0.0);
			jsonObject.addProperty("astDiffRecall", 0.0);
			jsonObject.addProperty("astDiffSimilarity", 0.0);
			return;
		}
		double overallSimilarity = computeOverallSimilarity(humanActions, toolActions, jsonObject);
		jsonObject.addProperty("astDiffSimilarity", overallSimilarity);
	}

	public static void handleRefactoringResult(String[] args) throws Exception {

		String refactoringJsonPath = "tmp/data/mockito/mockito_agent_refactoring_result_merge_v2.json";
//		String refactoringJsonPath = "tmp/data/checkstyle/checkstyle_baseline_result_merge_with_precision_recall.json";
		// 读取 JSON 文件
		String content = new String(Files.readAllBytes(Paths.get(refactoringJsonPath)));

		JsonArray jsonArray = JsonParser.parseString(content).getAsJsonArray();
		// 遍历 JsonArray
		Set<String> refactoringIds = new HashSet<>();
		for (int i = 0; i < jsonArray.size(); i++) {
			// 获取每个元素（JsonObject）
			JsonObject jsonObject = jsonArray.get(i).getAsJsonObject();
			String refactoringMinerResult = jsonObject.get("refactoringMinerResult").getAsString();
			String description = jsonObject.get("description").getAsString();
			String compileAndTestResult = jsonObject.get("compileAndTestResult").getAsString();
			String repairCompileAndTestResult = "";
			if (jsonObject.get("repairCompileAndTestResult")!=null){
				repairCompileAndTestResult = jsonObject.get("repairCompileAndTestResult").getAsString();
			}
			String moveMethodResultRefactoringMiner = "";
			if(jsonObject.get("moveMethodResultRefactoringMiner") != null){
				moveMethodResultRefactoringMiner = jsonObject.get("moveMethodResultRefactoringMiner").getAsString();
			}
			String refactoringId = jsonObject.get("uniqueId").getAsString();
			String codeBeforeRefactoring = jsonObject.get("sourceCodeBeforeForWhole").getAsString();
			String codeAfterRefactoring = jsonObject.get("sourceCodeAfterForWhole").getAsString();
			String methodCodeBeforeRefactoring = jsonObject.get("sourceCodeBeforeRefactoring").getAsString();
			String methodNameRefactored = jsonObject.get("methodNameBefore").getAsString();
			if (refactoringIds.contains(refactoringId)) {
				continue;
			}
			refactoringIds.add(refactoringId);
			String refactoringType = description.split("\t")[0];
			String agentCode = "";
			if (!StringUtils.equals(refactoringType, "Inline Method") && !StringUtils.equals(refactoringType, "Move Method") && !StringUtils.equals(refactoringType, "Extract Method") && !StringUtils.equals(refactoringType, "Extract And Move Method")) {
				continue;
			}
			if(StringUtils.equals(moveMethodResultRefactoringMiner, "false")){
				continue;
			}
			if (refactoringMinerResult.equals("false")) {
				continue;
			}
			if (compileAndTestResult.equals("true")) {
				agentCode = jsonObject.get("agentRefactoredCode").getAsString();
			}
			if (StringUtils.equals(repairCompileAndTestResult, "true")) {
				agentCode = jsonObject.get("repairRefactoredCode").getAsString();
			}
			if(StringUtils.equals(refactoringType, "Move Method")){
				jsonObject.addProperty("toolAfterCode", methodCodeBeforeRefactoring);
				jsonObject.addProperty("astDiffPrecision", 1.0);
				jsonObject.addProperty("astDiffRecall", 1.0);
				jsonObject.addProperty("astDiffSimilarity", 1.0);
				continue;
			}
			Path path = Paths.get("tmp/data/codeBefore/test.java");
			Files.write(path, codeBeforeRefactoring.getBytes(), StandardOpenOption.CREATE);
			path = Paths.get("tmp/data/humanRefactoring/test.java");
			Files.write(path, codeAfterRefactoring.getBytes(), StandardOpenOption.CREATE);
			path = Paths.get("tmp/data/toolRefactoring/test.java");
			Files.write(path, agentCode.getBytes(), StandardOpenOption.CREATE);
			try {
				detectAST("tmp/data/codeBefore/test.java", "tmp/data/humanRefactoring/test.java", "tmp/data/toolRefactoring/test.java", jsonObject, refactoringType);
			}catch (Exception e){
				System.out.println("Error: " + refactoringId);
				jsonObject.addProperty("astDiffPrecision", 0.0);
				jsonObject.addProperty("astDiffRecall", 0.0);
				jsonObject.addProperty("astDiffSimilarity", 0.0);
			}
			String fileName	= "checkstyle.java";
			GitHistoryRefactoringMiner detector = new GitHistoryRefactoringMinerImpl();
			Map<String, String> fileContentsBefore = new HashMap<>();
			Map<String, String> fileContentsCurrent = new HashMap<>();
			fileContentsBefore.put(fileName, codeBeforeRefactoring);
			fileContentsCurrent.put(fileName, agentCode);
			String finalAgentCode = agentCode;
			if(StringUtils.equals(refactoringType, "Extract And Move Method")){
				refactoringType = "Extract Method";
			}
			String finalRefactoringType = refactoringType;
			detector.detectAtFileContents(fileContentsBefore, fileContentsCurrent, new RefactoringHandler() {
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {
					if(!refactorings.isEmpty()) {
						for(Refactoring refactoring : refactorings) {

							if(StringUtils.equals(refactoring.getRefactoringType().getDisplayName(), finalRefactoringType)) {
								if (refactoring instanceof ExtractOperationRefactoring) {
									ExtractOperationRefactoring extractOperationRefactoring = (ExtractOperationRefactoring) refactoring;
									String className = extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName();
									String methodName = className + "#" + extractOperationRefactoring.getSourceOperationBeforeExtraction().getName();
									if (StringUtils.equals(methodNameRefactored, methodName)) {
										LocationInfo locationInfoAfter = extractOperationRefactoring.getSourceOperationAfterExtraction().getLocationInfo();
										LocationInfo locationInfoExtracted = extractOperationRefactoring.getExtractedOperation().getLocationInfo();
										String sourceCodeAfterForTool = RefactoringAnalysis.getLines(finalAgentCode, locationInfoAfter.getStartLine(), locationInfoAfter.getEndLine());
										String sourceCodeExtracted = RefactoringAnalysis.getLines(finalAgentCode, locationInfoExtracted.getStartLine(), locationInfoExtracted.getEndLine());

										String toolAfterCode = sourceCodeAfterForTool + "\n" + sourceCodeExtracted;
										jsonObject.addProperty("toolAfterCode", toolAfterCode);
									}
								}else if (refactoring instanceof InlineOperationRefactoring) {
									InlineOperationRefactoring inlineOperationRefactoring = (InlineOperationRefactoring) refactoring;
									String className = inlineOperationRefactoring.getInlinedOperation().getClassName();
									String methodName = className + "#" + inlineOperationRefactoring.getInlinedOperation().getName();
									if (StringUtils.equals(methodNameRefactored, methodName)) {
										LocationInfo locationInfoAfter = inlineOperationRefactoring.getTargetOperationAfterInline().getLocationInfo();
										String sourceCodeAfterForTool = RefactoringAnalysis.getLines(finalAgentCode, locationInfoAfter.getStartLine(), locationInfoAfter.getEndLine());
										jsonObject.addProperty("toolAfterCode", sourceCodeAfterForTool);
									}
								}else{

								}


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
			});

		}
		JsonUtil.writeJsonToFile(refactoringJsonPath, jsonArray);
	}

	public static double computeOverallSimilarity(List<Action> groundTruth, List<Action> prediction, JsonObject jsonObject) {
		int matched = 0;
		Set<Action> matchedActionGT = new HashSet<>();
		for (Action actionPred : prediction) {
			double maxSim = 0.0;
			Action bestMatch = null;

			for (Action actionGT : groundTruth) {
				double sim = computeActionSimilarity(actionGT, actionPred);
				if (sim > maxSim) {
					maxSim = sim;
					bestMatch = actionGT;
				}
			}

			if (maxSim > 0.7 && bestMatch != null) {
				matched++;
			}
		}

		// 计算 Precision 和 Recall
		double precision = prediction.isEmpty() ? 1.0 : (double) matched / prediction.size();
		double recall = groundTruth.isEmpty() ? 1.0 : (double) matched / groundTruth.size();

		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		if(recall >1.0){
			System.out.println("recall > 1.0");
			recall = 1.0;
		}
		jsonObject.addProperty("astDiffPrecision", precision);
		jsonObject.addProperty("astDiffRecall", recall);
		return 2 * (precision * recall) / (precision + recall + 1e-9); // 防止除0
	}

	public static double computeActionSimilarity(Action action1, Action action2) {
		// 1. 操作类型相似性
		double typeSim = action1.getClass().equals(action2.getClass()) ? 1.0 : 0.0;

		// 2. AST 结构相似性
		double astSim = action1.getNode().getType() == action2.getNode().getType() ? 1.0 : 0.0;

		// 3. 文本相似度
		String label1 = action1.getNode().getLabel();
		String label2 = action2.getNode().getLabel();
		double textSim = computeLevenshteinSimilarity(label1, label2);

		// 4. 上下文相似度
		boolean sameParent = action1.getNode().getParent().getType() == action2.getNode().getParent().getType();
		double contextSim = sameParent ? 1.0 : 0.0;

		// 计算最终相似度（可以调整权重）
		return 0.3 * typeSim + 0.3 * astSim + 0.3 * textSim + 0.1 * contextSim;
	}

	public static double computeLevenshteinSimilarity(String str1, String str2) {
		int len1 = str1.length();
		int len2 = str2.length();
		if (len1 == 0 && len2 == 0) {
			return 1.0;
		}
		int[][] dp = new int[len1 + 1][len2 + 1];
		for (int i = 0; i <= len1; i++) {
			dp[i][0] = i;
		}
		for (int j = 0; j <= len2; j++) {
			dp[0][j] = j;
		}
		for (int i = 1; i <= len1; i++) {
			for (int j = 1; j <= len2; j++) {
				int cost = str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1;
				dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
			}
		}
		return 1.0 - (double) dp[len1][len2] / Math.max(len1, len2);
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
//		String skipFilePath = args[4];
//		String skipCommitsStr = new String(Files.readAllBytes(Paths.get(skipFilePath)));
		List<String> skipCommits = new ArrayList<>();
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
			String refactoringJsonPathWithSC = "tmp/data/output/" + projectName +"_move_and_inline_refactoring.json";
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
