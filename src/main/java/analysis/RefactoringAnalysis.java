package analysis;


import analysis.entity.Location;
import analysis.entity.RefactoringForAnalysis;
import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.UMLModel;
import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.decomposition.AbstractCall;
import gr.uom.java.xmi.decomposition.CompositeStatementObject;
import gr.uom.java.xmi.decomposition.OperationBody;
import gr.uom.java.xmi.diff.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.refactoringminer.api.PurityCheckResult;
import org.refactoringminer.api.PurityChecker;
import org.refactoringminer.api.Refactoring;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class RefactoringAnalysis {

    public static List<RefactoringForAnalysis> analysisRefactorings(String commitId, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent) {
        Map<String, RefactoringForAnalysis> filePathToRefactoring = new HashMap<>();
        List<RefactoringForAnalysis> refactoringsForAnalysis = new ArrayList<>();
        Set<String> commitFileLineUniqueIds = new HashSet<>();
        for (Refactoring refactoring : refactorings) {
            switch(refactoring.getRefactoringType()){
                case EXTRACT_OPERATION:
                case EXTRACT_AND_MOVE_OPERATION:
                    getMethodExtraction(commitId, refactoring, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent, filePathToRefactoring, commitFileLineUniqueIds, refactoringsForAnalysis);
                    break;
                case MOVE_OPERATION:
                case MOVE_AND_RENAME_OPERATION:
                    getMoveOperation(commitId, refactoring, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent, refactoringsForAnalysis);
                    break;
                case MOVE_AND_INLINE_OPERATION:
                case INLINE_OPERATION:
                    getInlineOperation(commitId, refactoring, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent, refactoringsForAnalysis);
                    break;
                case PUSH_DOWN_OPERATION:
                    getPushDownOperation(commitId, refactoring, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent, refactoringsForAnalysis);
                    break;
                case PULL_UP_OPERATION:
                    getPullUpOperation(commitId, refactoring, refactorings, modelDiff, fileContentsBefore, fileContentsCurrent, refactoringsForAnalysis);
                    break;
                default:
                    break;
            }

        }

        List<RefactoringForAnalysis> refactoringForAnalysisList = new ArrayList<>(filePathToRefactoring.values());
        refactoringForAnalysisList.addAll(refactoringsForAnalysis);
        return refactoringForAnalysisList;
    }

    private static void getMoveOperation(String commitId, Refactoring refactoring, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, List<RefactoringForAnalysis> refactoringsForAnalysis){
        if(isMoveMethodOrMoveRename(refactoring)){
            MoveOperationRefactoring moveRefactoring = (MoveOperationRefactoring) refactoring;
            LocationInfo locationInfoBefore = moveRefactoring.getOriginalOperation().getLocationInfo();
            LocationInfo locationInfoAfter = moveRefactoring.getMovedOperation().getLocationInfo();
            String filePathBefore = locationInfoBefore.getFilePath();
            String filePathAfter = locationInfoAfter.getFilePath();
            String sourceCodeBeforeForWhole = fileContentsBefore.get(filePathBefore);
            String sourceCodeAfterForWhole = fileContentsCurrent.get(filePathAfter);
            String sourceCodeBefore = getSourceCodeByLocationInfo(locationInfoBefore, filePathBefore, fileContentsBefore);
            String sourceCodeAfter = getSourceCodeByLocationInfo(locationInfoAfter, filePathAfter, fileContentsCurrent);
            String uniqueId = commitId + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine() + "_" + "_" + locationInfoAfter.getStartLine() + "_" + locationInfoAfter.getEndLine();
            String commitFileLineUniqueId = commitId + "_" + filePathBefore + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine();
            RefactoringForAnalysis refactoringForAnalysisOutput = new RefactoringForAnalysis();
            refactoringForAnalysisOutput.setFilePathBefore(filePathBefore);
            refactoringForAnalysisOutput.setFilePathAfter(filePathAfter);
            refactoringForAnalysisOutput.setType(moveRefactoring.getName());
            refactoringForAnalysisOutput.setUniqueId(uniqueId);
            refactoringForAnalysisOutput.setSourceCodeBeforeRefactoring(sourceCodeBefore);
            refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(sourceCodeAfter);
            refactoringForAnalysisOutput.setSourceCodeBeforeForWhole(sourceCodeBeforeForWhole);
            refactoringForAnalysisOutput.setSourceCodeAfterForWhole(sourceCodeAfterForWhole);
            String className = moveRefactoring.getOriginalOperation().getClassName();
            refactoringForAnalysisOutput.setCommitId(commitId);
            refactoringForAnalysisOutput.setClassNameBefore(className);
            refactoringForAnalysisOutput.setPackageNameBefore(className.substring(0, className.lastIndexOf('.')));
            refactoringForAnalysisOutput.setDiffSourceCodeSet(new HashSet<>());
            refactoringForAnalysisOutput.setInvokedMethodSet(new HashSet<>());
            refactoringForAnalysisOutput.setDescription(refactoring.toString());
            String methodName = className + "#" + moveRefactoring.getOriginalOperation().getName();
            setPackageAndClassInfo(className, modelDiff, refactoringForAnalysisOutput, methodName, moveRefactoring.getOriginalOperation().getBody());
            handlePureRefactoring(refactoring, refactorings, modelDiff, refactoringForAnalysisOutput);
            handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, null, fileContentsBefore, fileContentsCurrent, refactoringForAnalysisOutput);
//				filePathToRefactoring.put(filePathBefore, refactoringForAnalysisOutput);
//				commitFileLineUniqueIds.add(commitFileLineUniqueId);
            refactoringsForAnalysis.add(refactoringForAnalysisOutput);
        }
    }

    private static void getInlineOperation(String commitId, Refactoring refactoring, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, List<RefactoringForAnalysis> refactoringAnalysisList){
        if(isInlineMethodOrMoveInline(refactoring)){
            InlineOperationRefactoring inlineOperationRefactoring = (InlineOperationRefactoring) refactoring;
            LocationInfo locationInfoBefore = inlineOperationRefactoring.getTargetOperationBeforeInline().getLocationInfo();
            LocationInfo locationInfoAfter = inlineOperationRefactoring.getTargetOperationAfterInline().getLocationInfo();
            LocationInfo inlineOperationLocationInfo = inlineOperationRefactoring.getInlinedOperation().getLocationInfo();
            String filePathBefore = locationInfoBefore.getFilePath();
            String filePathAfter = locationInfoAfter.getFilePath();
            String sourceCodeBeforeForWhole = fileContentsBefore.get(filePathBefore);
            String sourceCodeAfterForWhole = fileContentsCurrent.get(filePathAfter);
            String sourceCodeBeforeInline = getSourceCodeByLocationInfo(inlineOperationLocationInfo, filePathBefore, fileContentsBefore);
            String sourceCodeBefore = getSourceCodeByLocationInfo(locationInfoBefore, filePathBefore, fileContentsBefore);
            String sourceCodeAfter = getSourceCodeByLocationInfo(locationInfoAfter, filePathAfter, fileContentsCurrent);
            String uniqueId = commitId + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine() + "_" + "_" + locationInfoAfter.getStartLine() + "_" + locationInfoAfter.getEndLine() + "_" + inlineOperationLocationInfo.getStartLine() + "_" + inlineOperationLocationInfo.getEndLine();
            RefactoringForAnalysis refactoringForAnalysisOutput = new RefactoringForAnalysis();
            refactoringForAnalysisOutput.setFilePathBefore(filePathBefore);
            refactoringForAnalysisOutput.setFilePathAfter(filePathAfter);
            refactoringForAnalysisOutput.setType(inlineOperationRefactoring.getName());
            refactoringForAnalysisOutput.setUniqueId(uniqueId);
            refactoringForAnalysisOutput.setSourceCodeBeforeRefactoring(sourceCodeBeforeInline);
            refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(sourceCodeAfter);
            refactoringForAnalysisOutput.setSourceCodeBeforeForWhole(sourceCodeBeforeForWhole);
            refactoringForAnalysisOutput.setSourceCodeAfterForWhole(sourceCodeAfterForWhole);
            String className = inlineOperationRefactoring.getInlinedOperation().getClassName();
            refactoringForAnalysisOutput.setCommitId(commitId);
            refactoringForAnalysisOutput.setClassNameBefore(className);
            refactoringForAnalysisOutput.setPackageNameBefore(className.substring(0, className.lastIndexOf('.')));
            refactoringForAnalysisOutput.setDiffSourceCodeSet(new HashSet<>());
            refactoringForAnalysisOutput.setInvokedMethodSet(new HashSet<>());
            refactoringForAnalysisOutput.setDescription(refactoring.toString());
            String methodName = className + "#" + inlineOperationRefactoring.getInlinedOperation().getName();
            setPackageAndClassInfo(className, modelDiff, refactoringForAnalysisOutput, methodName, inlineOperationRefactoring.getInlinedOperation().getBody());
            refactoringForAnalysisOutput.setMethodNameBefore(methodName);
            Set<String> methodNameSet = new HashSet<>();
            methodNameSet.add(methodName);
            refactoringForAnalysisOutput.setMethodNameBeforeSet(methodNameSet);
            handlePureRefactoring(refactoring, refactorings, modelDiff, refactoringForAnalysisOutput);
            handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, inlineOperationLocationInfo, fileContentsBefore, fileContentsCurrent, refactoringForAnalysisOutput);
//				filePathToRefactoring.put(filePathBefore, refactoringForAnalysisOutput);
//				commitFileLineUniqueIds.add(commitFileLineUniqueId);
            refactoringAnalysisList.add(refactoringForAnalysisOutput);
        }
    }


    private static void getPushDownOperation(String commitId, Refactoring refactoring, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, List<RefactoringForAnalysis> refactoringForAnalysisList){
        if(isPushDownMethod(refactoring)){
            PushDownOperationRefactoring pushDownOperationRefactoring = (PushDownOperationRefactoring) refactoring;
            LocationInfo locationInfoBefore = pushDownOperationRefactoring.getOriginalOperation().getLocationInfo();
            LocationInfo locationInfoAfter = pushDownOperationRefactoring.getMovedOperation().getLocationInfo();
            String filePathBefore = locationInfoBefore.getFilePath();
            String filePathAfter = locationInfoAfter.getFilePath();
            String sourceCodeBeforeForWhole = fileContentsBefore.get(filePathBefore);
            String sourceCodeAfterForWhole = fileContentsCurrent.get(filePathAfter);
            String sourceCodeBefore = getSourceCodeByLocationInfo(locationInfoBefore, filePathBefore, fileContentsBefore);
            String sourceCodeAfter = getSourceCodeByLocationInfo(locationInfoAfter, filePathAfter, fileContentsCurrent);
            String uniqueId = commitId + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine() + "_" + "_" + locationInfoAfter.getStartLine() + "_" + locationInfoAfter.getEndLine();
            String commitFileLineUniqueId = commitId + "_" + filePathBefore + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine();
            RefactoringForAnalysis refactoringForAnalysisOutput = new RefactoringForAnalysis();
            refactoringForAnalysisOutput.setFilePathBefore(filePathBefore);
            refactoringForAnalysisOutput.setFilePathAfter(filePathAfter);
            refactoringForAnalysisOutput.setType(pushDownOperationRefactoring.getName());
            refactoringForAnalysisOutput.setUniqueId(uniqueId);
            refactoringForAnalysisOutput.setSourceCodeBeforeRefactoring(sourceCodeBefore);
            refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(sourceCodeAfter);
            refactoringForAnalysisOutput.setSourceCodeBeforeForWhole(sourceCodeBeforeForWhole);
            refactoringForAnalysisOutput.setSourceCodeAfterForWhole(sourceCodeAfterForWhole);
            String className = pushDownOperationRefactoring.getOriginalOperation().getClassName();
            refactoringForAnalysisOutput.setCommitId(commitId);
            refactoringForAnalysisOutput.setClassNameBefore(className);
            refactoringForAnalysisOutput.setPackageNameBefore(className.substring(0, className.lastIndexOf('.')));
            refactoringForAnalysisOutput.setDiffSourceCodeSet(new HashSet<>());
            refactoringForAnalysisOutput.setInvokedMethodSet(new HashSet<>());
            refactoringForAnalysisOutput.setDescription(refactoring.toString());
            String methodName = className + "#" + pushDownOperationRefactoring.getOriginalOperation().getName();
            setPackageAndClassInfo(className, modelDiff, refactoringForAnalysisOutput, methodName, pushDownOperationRefactoring.getOriginalOperation().getBody());
            handlePureRefactoring(refactoring, refactorings, modelDiff, refactoringForAnalysisOutput);
            handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, null, fileContentsBefore, fileContentsCurrent, refactoringForAnalysisOutput);
//				filePathToRefactoring.put(filePathBefore, refactoringForAnalysisOutput);
//				commitFileLineUniqueIds.add(commitFileLineUniqueId);
            refactoringForAnalysisList.add(refactoringForAnalysisOutput);
        }
    }

    private static void getPullUpOperation(String commitId, Refactoring refactoring, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, List<RefactoringForAnalysis> refactoringForAnalysisList){
        if(isPullUpMethod(refactoring)){
            PullUpOperationRefactoring pullUpOperationRefactoring = (PullUpOperationRefactoring) refactoring;
            LocationInfo locationInfoBefore = pullUpOperationRefactoring.getOriginalOperation().getLocationInfo();
            LocationInfo locationInfoAfter = pullUpOperationRefactoring.getMovedOperation().getLocationInfo();
            String filePathBefore = locationInfoBefore.getFilePath();
            String filePathAfter = locationInfoAfter.getFilePath();
            String sourceCodeBeforeForWhole = fileContentsBefore.get(filePathBefore);
            String sourceCodeAfterForWhole = fileContentsCurrent.get(filePathAfter);
            String sourceCodeBefore = getSourceCodeByLocationInfo(locationInfoBefore, filePathBefore, fileContentsBefore);
            String sourceCodeAfter = getSourceCodeByLocationInfo(locationInfoAfter, filePathAfter, fileContentsCurrent);
            String uniqueId = commitId + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine() + "_" + "_" + locationInfoAfter.getStartLine() + "_" + locationInfoAfter.getEndLine();
            String commitFileLineUniqueId = commitId + "_" + filePathBefore + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine();
            RefactoringForAnalysis refactoringForAnalysisOutput = new RefactoringForAnalysis();
            refactoringForAnalysisOutput.setFilePathBefore(filePathBefore);
            refactoringForAnalysisOutput.setFilePathAfter(filePathAfter);
            refactoringForAnalysisOutput.setType(pullUpOperationRefactoring.getName());
            refactoringForAnalysisOutput.setUniqueId(uniqueId);
            refactoringForAnalysisOutput.setSourceCodeBeforeRefactoring(sourceCodeBefore);
            refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(sourceCodeAfter);
            refactoringForAnalysisOutput.setSourceCodeBeforeForWhole(sourceCodeBeforeForWhole);
            refactoringForAnalysisOutput.setSourceCodeAfterForWhole(sourceCodeAfterForWhole);
            String className = pullUpOperationRefactoring.getOriginalOperation().getClassName();
            refactoringForAnalysisOutput.setCommitId(commitId);
            refactoringForAnalysisOutput.setClassNameBefore(className);
            refactoringForAnalysisOutput.setPackageNameBefore(className.substring(0, className.lastIndexOf('.')));
            refactoringForAnalysisOutput.setDiffSourceCodeSet(new HashSet<>());
            refactoringForAnalysisOutput.setInvokedMethodSet(new HashSet<>());
            refactoringForAnalysisOutput.setDescription(refactoring.toString());
            String methodName = className + "#" + pullUpOperationRefactoring.getOriginalOperation().getName();
            setPackageAndClassInfo(className, modelDiff, refactoringForAnalysisOutput, methodName, pullUpOperationRefactoring.getOriginalOperation().getBody());
            handlePureRefactoring(refactoring, refactorings, modelDiff, refactoringForAnalysisOutput);
            handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, null, fileContentsBefore, fileContentsCurrent, refactoringForAnalysisOutput);
//				filePathToRefactoring.put(filePathBefore, refactoringForAnalysisOutput);
//				commitFileLineUniqueIds.add(commitFileLineUniqueId);
            refactoringForAnalysisList.add(refactoringForAnalysisOutput);
        }
    }

    private static boolean isMethodExtractionOrMoveExtraction(Refactoring refactoring) {
        return refactoring instanceof ExtractOperationRefactoring extractOperationRefactoring && (StringUtils.equalsIgnoreCase(extractOperationRefactoring.getName(), "Extract Method") || StringUtils.equalsIgnoreCase(extractOperationRefactoring.getName(), "Extract And Move Method"));
    }

    private static boolean isMoveMethodOrMoveRename(Refactoring refactoring) {
        return refactoring instanceof MoveOperationRefactoring moveOperationRefactoring && (StringUtils.equalsIgnoreCase(moveOperationRefactoring.getName(), "Move Method") || StringUtils.equalsIgnoreCase(moveOperationRefactoring.getName(), "Move And Rename Method"));
    }

    private static boolean isInlineMethodOrMoveInline(Refactoring refactoring) {
        return refactoring instanceof InlineOperationRefactoring inlineOperationRefactoring && (StringUtils.equalsIgnoreCase(inlineOperationRefactoring.getName(), "Inline Method")|| StringUtils.equalsIgnoreCase(inlineOperationRefactoring.getName(), "Move And Inline Method"));
    }

    private static boolean isPushDownMethod(Refactoring refactoring) {
        return refactoring instanceof PushDownOperationRefactoring pushDownOperationRefactoring && StringUtils.equalsIgnoreCase(pushDownOperationRefactoring.getName(), "Push Down Method");
    }

    private static boolean isPullUpMethod(Refactoring refactoring) {
        return refactoring instanceof PullUpOperationRefactoring pullUpOperationRefactoring && StringUtils.equalsIgnoreCase(pullUpOperationRefactoring.getName(), "Pull Up Method");
    }

    private static void getMethodExtraction(String commitId, Refactoring refactoring, List<Refactoring> refactorings, UMLModelDiff modelDiff, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, Map<String, RefactoringForAnalysis> filePathToRefactoring, Set<String> commitFileLineUniqueIds, List<RefactoringForAnalysis> refactoringsForAnalysis) {
        if (isMethodExtractionOrMoveExtraction(refactoring)) {
            ExtractOperationRefactoring extractOperationRefactoring = (ExtractOperationRefactoring) refactoring;
            LocationInfo locationInfoBefore = extractOperationRefactoring.getSourceOperationBeforeExtraction().getLocationInfo();
            LocationInfo locationInfoAfter = extractOperationRefactoring.getSourceOperationAfterExtraction().getLocationInfo();
            LocationInfo locationInfoExtracted = extractOperationRefactoring.getExtractedOperation().getLocationInfo();
            String filePathBefore = locationInfoBefore.getFilePath();
            String filePathAfter = locationInfoAfter.getFilePath();
            String sourceCodeBeforeForWhole = fileContentsBefore.get(filePathBefore);
            String sourceCodeAfterForWhole = fileContentsCurrent.get(filePathAfter);
            String sourceCodeBefore = getSourceCodeByLocationInfo(locationInfoBefore, filePathBefore, fileContentsBefore);
            String sourceCodeAfter = getSourceCodeByLocationInfo(locationInfoAfter, filePathAfter, fileContentsCurrent);
            String extractedCode = getSourceCodeByLocationInfo(locationInfoExtracted, filePathAfter, fileContentsCurrent);
            String uniqueId = commitId + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine() + "_" + locationInfoExtracted.getStartLine() + "_" + locationInfoExtracted.getEndLine() + "_" + locationInfoAfter.getStartLine() + "_" + locationInfoAfter.getEndLine();
//            String commitFileLineUniqueId = commitId + "_" + filePathBefore + "_" + locationInfoBefore.getStartLine() + "_" + locationInfoBefore.getEndLine();
//            if (filePathToRefactoring.containsKey(filePathBefore)) {
//                RefactoringForAnalysis refactoringForAnalysisOutput = filePathToRefactoring.get(filePathBefore);
//                if (!commitFileLineUniqueIds.contains(commitFileLineUniqueId)) {
//                    commitFileLineUniqueIds.add(commitFileLineUniqueId);
//                    refactoringForAnalysisOutput.setSourceCodeBeforeRefactoring(refactoringForAnalysisOutput.getSourceCodeBeforeRefactoring() + "\n" + sourceCodeBefore);
//                    Set<String> diffSourceCodeSet = refactoringForAnalysisOutput.getDiffSourceCodeSet();
//                    if(!diffSourceCodeSet.contains(extractedCode)){
//                        refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(refactoringForAnalysisOutput.getSourceCodeAfterRefactoring() + "\n" + sourceCodeAfter + '\n' + extractedCode);
//                    }else{
//                        diffSourceCodeSet.add(extractedCode);
//                        refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(refactoringForAnalysisOutput.getSourceCodeAfterRefactoring() + "\n" + sourceCodeAfter);
//                    }
//                    String className = extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName();
//                    refactoringForAnalysisOutput.setDiffSourceCodeSet(diffSourceCodeSet);
//                    refactoringForAnalysisOutput.setUniqueId(refactoringForAnalysisOutput.getUniqueId());
//                    String methodName = className + "#" + extractOperationRefactoring.getSourceOperationBeforeExtraction().getName();
//                    setPackageAndClassInfo(className, modelDiff, refactoringForAnalysisOutput, methodName, extractOperationRefactoring.getSourceOperationBeforeExtraction().getBody());
//                    handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, locationInfoExtracted, fileContentsBefore, fileContentsCurrent, refactoringForAnalysisOutput);
//                    handlePureRefactoring(refactoring, refactorings, modelDiff, refactoringForAnalysisOutput);
//                }
//            } else {
            RefactoringForAnalysis refactoringForAnalysisOutput = new RefactoringForAnalysis();
            refactoringForAnalysisOutput.setFilePathBefore(filePathBefore);
            refactoringForAnalysisOutput.setFilePathAfter(filePathAfter);
            refactoringForAnalysisOutput.setType(extractOperationRefactoring.getName());
            refactoringForAnalysisOutput.setUniqueId(uniqueId);
            refactoringForAnalysisOutput.setSourceCodeBeforeRefactoring(sourceCodeBefore);
            refactoringForAnalysisOutput.setSourceCodeAfterRefactoring(sourceCodeAfter + '\n' + extractedCode);
            refactoringForAnalysisOutput.setSourceCodeBeforeForWhole(sourceCodeBeforeForWhole);
            refactoringForAnalysisOutput.setSourceCodeAfterForWhole(sourceCodeAfterForWhole);
            String className = extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName();
            refactoringForAnalysisOutput.setCommitId(commitId);
            refactoringForAnalysisOutput.setClassNameBefore(extractOperationRefactoring.getSourceOperationBeforeExtraction().getClassName());
            refactoringForAnalysisOutput.setPackageNameBefore(className.substring(0, className.lastIndexOf('.')));
            refactoringForAnalysisOutput.setDiffSourceCodeSet(new HashSet<>(Collections.singletonList(extractedCode)));
            refactoringForAnalysisOutput.setInvokedMethodSet(new HashSet<>());
            refactoringForAnalysisOutput.setDescription(refactoring.toString());
            String methodName = className + "#" + extractOperationRefactoring.getSourceOperationBeforeExtraction().getName();
            setPackageAndClassInfo(className, modelDiff, refactoringForAnalysisOutput, methodName, extractOperationRefactoring.getSourceOperationBeforeExtraction().getBody());
            handlePureRefactoring(refactoring, refactorings, modelDiff, refactoringForAnalysisOutput);
            handleDiffCode(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, locationInfoExtracted, fileContentsBefore, fileContentsCurrent, refactoringForAnalysisOutput);
//            filePathToRefactoring.put(filePathBefore, refactoringForAnalysisOutput);
//            commitFileLineUniqueIds.add(commitFileLineUniqueId);
//            }
            refactoringsForAnalysis.add(refactoringForAnalysisOutput);
        }
    }

    private static void handleDiffCode(String filePathBefore, LocationInfo locationInfoBefore, String filePathAfter, LocationInfo locationInfoAfter, LocationInfo locationInfoExtracted, Map<String, String> fileContentsBefore, Map<String, String> fileContentsCurrent, RefactoringForAnalysis refactoringForAnalysisOutput) {
        handleLocations(filePathBefore, locationInfoBefore, filePathAfter, locationInfoAfter, locationInfoExtracted, refactoringForAnalysisOutput);
        List<Location> locations = refactoringForAnalysisOutput.getDiffLocations();
        if(CollectionUtils.isEmpty(locations)){
            refactoringForAnalysisOutput.setDiffSourceCode("");
            return ;
        }
        String diffCode = getDiffCodeContent(filePathBefore,fileContentsBefore, filePathAfter, fileContentsCurrent, locations);
        refactoringForAnalysisOutput.setDiffSourceCode(diffCode);
    }

    private static void handleLocations(String filePathBefore, LocationInfo locationInfoBefore, String filePathAfter, LocationInfo locationInfoAfter, LocationInfo locationInfoExtracted, RefactoringForAnalysis refactoringForAnalysisOutput) {
        List<Location> locations = refactoringForAnalysisOutput.getDiffLocations() == null ? new ArrayList<>() : refactoringForAnalysisOutput.getDiffLocations();
        Location locationBefore = new Location();
        locationBefore.setFilePath(filePathBefore);
        locationBefore.setStartLine(locationInfoBefore.getStartLine());
        locationBefore.setEndLine(locationInfoBefore.getEndLine());
        locations.add(locationBefore);
        Location locationAfter = new Location();
        locationAfter.setFilePath(filePathAfter);
        locationAfter.setStartLine(locationInfoAfter.getStartLine());
        locationAfter.setEndLine(locationInfoAfter.getEndLine());
        locations.add(locationAfter);
        if(locationInfoExtracted != null){
            Location locationExtracted = new Location();
            locationExtracted.setFilePath(filePathAfter);
            locationExtracted.setStartLine(locationInfoExtracted.getStartLine());
            locationExtracted.setEndLine(locationInfoExtracted.getEndLine());
            locations.add(locationExtracted);
        }
        refactoringForAnalysisOutput.setDiffLocations(locations);
    }


    private static void handlePureRefactoring(Refactoring refactoring, List<Refactoring> refactorings, UMLModelDiff modelDiff, RefactoringForAnalysis refactoringForAnalysisOutput){
        PurityCheckResult result = PurityChecker.check(refactoring, refactorings, modelDiff);
        List<PurityCheckResult> purityCheckResults = refactoringForAnalysisOutput.getPurityCheckResultList();
        if(CollectionUtils.isEmpty(purityCheckResults)){
            purityCheckResults = new ArrayList<>();
        }
        purityCheckResults.add(result);
        if(result != null && result.isPure()){
            refactoringForAnalysisOutput.setPureRefactoring(true);
            refactoringForAnalysisOutput.setPurityCheckResultList(purityCheckResults);
            return;
        }
        boolean isPure = refactoringForAnalysisOutput.getPureRefactoring() == null ? false : refactoringForAnalysisOutput.getPureRefactoring();
        refactoringForAnalysisOutput.setPureRefactoring(isPure || false);
        refactoringForAnalysisOutput.setPurityCheckResultList(purityCheckResults);
    }

//    private boolean isPureExtractOperation(CommitMatcher matcher, Refactoring refactoring, RefactoringForAnalysis refactoringForAnalysisOutput) {
//        if(CollectionUtils.isEmpty(matcher.pureRefactorings)){
//            Set<String> descriptionSet = refactoringForAnalysisOutput.getDescriptionSet() == null ? new HashSet<>() : refactoringForAnalysisOutput.getDescriptionSet();
//            descriptionSet.addAll(normalize(refactoring.toString()));
//            refactoringForAnalysisOutput.setDescriptionSet(descriptionSet);
//            refactoringForAnalysisOutput.setDescription(String.join("\n", descriptionSet));
//            return false;
//        }
//        Set<String> refactoringsFound = new HashSet<String>(normalize(refactoring.toString()));
//
//        for (String expectedRefactoring : matcher.pureRefactorings) {
//            if (refactoringsFound.contains(expectedRefactoring)) {
//                Set<String> descriptionSet = refactoringForAnalysisOutput.getDescriptionSet() == null ? new HashSet<>() : refactoringForAnalysisOutput.getDescriptionSet();
//                descriptionSet.add(expectedRefactoring);
//                refactoringForAnalysisOutput.setDescriptionSet(descriptionSet);
//                refactoringForAnalysisOutput.setDescription(String.join("\n", descriptionSet));
//                return true;
//            }
//        }
//        return false;
//    }

    private static void setPackageAndClassInfo(String className, UMLModelDiff modelDiff, RefactoringForAnalysis refactoringForAnalysisOutput, String methodName, OperationBody body) {
        UMLModel parentModel = modelDiff.getParentModel();
        Map<String, UMLOperation> operationMap = new HashMap<>();
        List<String> classNameList = new ArrayList<>();
        parentModel.getClassList().forEach(umlClass -> {
            String classNameBefore = umlClass.getName();
            if(StringUtils.equals(classNameBefore, className)) {
                String packageName = refactoringForAnalysisOutput.getPackageNameBefore();
                if(!StringUtils.equals(packageName, umlClass.getPackageName())) {
                    refactoringForAnalysisOutput.setPackageNameBefore(packageName + '\n' + umlClass.getPackageName());
                }
                Set<String> refactoringOutputClassSignatureBeforeSet = refactoringForAnalysisOutput.getClassSignatureBeforeSet() == null ? new HashSet<>() : refactoringForAnalysisOutput.getClassSignatureBeforeSet();
                String classSignatureNew = removeLastCharacter(umlClass.getActualSignature());
                refactoringOutputClassSignatureBeforeSet.add(classSignatureNew);
                refactoringForAnalysisOutput.setClassSignatureBeforeSet(refactoringOutputClassSignatureBeforeSet);
                refactoringForAnalysisOutput.setClassSignatureBefore(String.join("\n", refactoringOutputClassSignatureBeforeSet));

                Set<String> classNameSetBefore = refactoringForAnalysisOutput.getClassNameBeforeSet() == null ? new HashSet<>() : refactoringForAnalysisOutput.getClassNameBeforeSet();
                classNameSetBefore.add(className);
                refactoringForAnalysisOutput.setClassNameBeforeSet(classNameSetBefore);
                refactoringForAnalysisOutput.setClassNameBefore(String.join("\n", classNameSetBefore));
            }
            umlClass.getOperations().forEach(operation -> {
                operationMap.put(operation.getClassName() + '#' + operation.getName(), operation);
            });
            classNameList.add(umlClass.getName());
        });
        Set<String> methodNameSet = refactoringForAnalysisOutput.getMethodNameBeforeSet() == null ? new HashSet<>() : refactoringForAnalysisOutput.getMethodNameBeforeSet();;
        methodNameSet.add(methodName);
        refactoringForAnalysisOutput.setMethodNameBeforeSet(methodNameSet);
        refactoringForAnalysisOutput.setMethodNameBefore(String.join("\n", methodNameSet));
        if(body == null) {
            return;
        }
        CompositeStatementObject compositeStatement = body.getCompositeStatement();
        List<AbstractCall> allMethodInvocations = compositeStatement.getAllMethodInvocations();
        if(allMethodInvocations.isEmpty()) {
            return;
        }
        allMethodInvocations.forEach(statement -> {
            String invokeMethodName = statement.getName();
            for (String c : classNameList) {
                if (operationMap.containsKey(c + '#' + invokeMethodName)) {
                    UMLOperation operation = operationMap.get(c + '#' + invokeMethodName);
                    String invokeMethod = "methodSignature: " + c + "#" + invokeMethodName + "\n methodBody: " + operation.getActualSignature();
                    if(!ObjectUtils.isEmpty(operation.getBody())){
                        invokeMethod += operation.getBody().getCompositeStatement().bodyStringRepresentation().stream().reduce("\n", String::concat);
                    }
                    Set<String> invokeMethodSet = refactoringForAnalysisOutput.getInvokedMethodSet();
                    invokeMethodSet.add(invokeMethod);
                    refactoringForAnalysisOutput.setInvokedMethod(String.join("\n", invokeMethodSet));
                }
            }
        });
    }

    public static String removeLastCharacter(String str) {
        if (str != null && !str.isEmpty()) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    public static String getSourceCodeByLocationInfo(LocationInfo locationInfo, String filePath, Map<String, String> fileContents) {
        String fileContent = fileContents.get(filePath);
        if (fileContent != null) {
            int startLine = locationInfo.getStartLine();
            int endLine = locationInfo.getEndLine();
            return getLines(fileContent, startLine, endLine);
        }
        return null;
    }

    /**
     * 获取字符串中指定行范围的内容
     *
     * @param filePath  文件路径
     * @param fileContents 文件内容
     * @return 指定行范围的内容，若行号超出范围则返回空字符串
     */
    public static String getSourceCodeByFilePath(String filePath, Map<String, String> fileContents) {
        String fileContent = fileContents.get(filePath);
        if (fileContent != null) {
            return fileContent;
        }
        return null;
    }
    /**
     * 获取字符串中指定行范围的内容
     *
     * @param input  输入的字符串
     * @param startLine 开始行号（1-based）
     * @param endLine   结束行号（1-based）
     * @return 指定行范围的内容，若行号超出范围则返回空字符串
     */
    public static String getLines(String input, int startLine, int endLine) {
        // 将输入字符串按行分割
        String[] lines = input.split("\n");

        // 检查行号是否在有效范围内
        if (startLine < 1 || endLine < startLine || endLine > lines.length) {
            return ""; // 行号超出范围，返回空字符串
        }

        // 构建结果
        StringBuilder result = new StringBuilder();
        for (int i = startLine - 1; i < endLine; i++) { // 注意：数组索引从0开始
            result.append(lines[i]).append("\n"); // 添加换行符
        }
        return result.toString().trim(); // 返回结果并去掉末尾的换行符
    }


    /**
     * 获取两个文件中指定行范围的代码内容
     *
     * @param filePathBefore  原文件路径
     * @param fileContentsBefore 原文件内容
     * @param filePathAfter    当前文件路径
     * @param fileContentsCurrent 当前文件内容
     * @param locations 代码位置信息
     * @return 指定行范围的代码内容
     */
    private static String getDiffCodeContent(String filePathBefore, Map<String, String> fileContentsBefore,
                                             String filePathAfter, Map<String, String> fileContentsCurrent, List<Location> locations)
    {
        String sourceCodeBefore = getSourceCodeByFilePath(filePathBefore, fileContentsBefore);
        String sourceCodeAfter = getSourceCodeByFilePath(filePathAfter, fileContentsCurrent);
        return extractUnionWithLineNumbers(getLineList(sourceCodeBefore), getLineList(sourceCodeAfter), locations);
    }

    private static void addLineRange(Set<Integer> lineNumbers, int start, int end)
    {
        for (int i = start; i <= end; i++)
        {
            lineNumbers.add(i);
        }
    }

    private static String extractUnionWithLineNumbers(
            List<String> oldContent, List<String> newContent, List<Location> locations) {

        // 使用 TreeSet 存储所有行号，并自动排序去重
        Set<Integer> unionLines = new TreeSet<>();
        for (Location loc : locations) {
            addLineRange(unionLines, loc.getStartLine(), loc.getEndLine());
        }

        // 用 StringBuilder 构建结果字符串
        StringBuilder result = new StringBuilder();

        // 用于临时存储删除和新增块
        List<String> deletedBlock = new ArrayList<>();
        List<String> addedBlock = new ArrayList<>();

        for (int line : unionLines) {
            String oldLine = line <= oldContent.size() ? oldContent.get(line - 1) : null;
            String newLine = line <= newContent.size() ? newContent.get(line - 1) : null;

            if (oldLine != null && oldLine.equals(newLine)) {
                // 输出当前的删除和新增块，然后打印相同的行
                flushBlock(result, deletedBlock);
                flushBlock(result, addedBlock);
                result.append(String.format("  %4d: %s\n", line, oldLine));
            } else {
                if(oldLine != null){
                    deletedBlock.add(String.format("- %4d: %s", line, oldLine));
                }
                if (newLine != null) {
                    addedBlock.add(String.format("+ %4d: %s", line, newLine));
                }
            }
        }

        // 输出最后的删除和新增块
        flushBlock(result, deletedBlock);
        flushBlock(result, addedBlock);

        return result.toString();
    }

    /**
     * 输出当前块中的所有行，并清空块。
     */
    private static void flushBlock(StringBuilder result, List<String> block) {
        if (!block.isEmpty()) {
            for (String line : block) {
                result.append(line).append("\n");
            }
            block.clear();
        }
    }



    private static List<String> getLineList(String input) {
        return Arrays.asList(input.split("\n"));
    }
}
