package analysis;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CallGraphExtractor {
    static class MethodNode {
        String methodId;
        String className;
        String methodName;
        List<String> paramTypes;
        String filePath;
        int startLine;
        String sourceCode;
    }

    static class EdgeRecord {
        String src;
        String tgt;
    }

    static class CallGraphJson {
        String commitId;
        List<MethodNode> nodes = new ArrayList<>();
        List<EdgeRecord> edges = new ArrayList<>();
    }

    public static void extractCallGraph(String commitId, String classesDir, String sourceRoot, String outputJsonPath) throws IOException {
        Map<String, MethodNode> methodMap = new HashMap<>();
        List<EdgeRecord> edgeList = new ArrayList<>();

        Options.v().set_prepend_classpath(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_whole_program(true);
        Options.v().set_app(true);
        Options.v().set_process_dir(Collections.singletonList(classesDir));
        Options.v().setPhaseOption("cg", "cha");
        Options.v().set_no_bodies_for_excluded(true);

    // 1. 加载 class
        Scene.v().loadNecessaryClasses();

    // 2. 构建 entry points（所有 public 方法）
        List<SootMethod> entryPoints = new ArrayList<>();
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (sm.isConcrete()) {
                    entryPoints.add(sm);
                }
            }
        }
        Scene.v().setEntryPoints(entryPoints);

        PackManager.v().runPacks();

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(config);
        CallGraph cg = Scene.v().getCallGraph();
        for (Edge edge : cg) {
            SootMethod src = edge.src();
            SootMethod tgt = edge.tgt();
            processMethod(src, sourceRoot, methodMap);
            processMethod(tgt, sourceRoot, methodMap);

            EdgeRecord er = new EdgeRecord();
            er.src = getMethodId(src);
            er.tgt = getMethodId(tgt);
            edgeList.add(er);
        }

        CallGraphJson output = new CallGraphJson();
        output.commitId = commitId;
        output.nodes.addAll(methodMap.values());
        output.edges.addAll(edgeList);

        Files.createDirectories(Paths.get(outputJsonPath).getParent());
        try (Writer writer = new FileWriter(outputJsonPath)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(output, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processMethod(SootMethod method, String sourceRoot, Map<String, MethodNode> methodMap) {
        String methodId = getMethodId(method);
        if (methodMap.containsKey(methodId)) return;

        MethodNode node = new MethodNode();
        node.methodId = methodId;
        node.className = method.getDeclaringClass().getName();
        node.methodName = method.getName();
        node.paramTypes = method.getParameterTypes().stream()
                .map(Type::toString)
                .collect(Collectors.toList());

        try {
            String className = method.getDeclaringClass().getName();
            Path sourceFile = Paths.get(sourceRoot, className.replace('.', '/') + ".java");
            if (!Files.exists(sourceFile)) return;

            CompilationUnit cu = StaticJavaParser.parse(sourceFile.toFile());

            Optional<MethodDeclaration> opt = cu.findAll(MethodDeclaration.class).stream()
                    .filter(md -> looseMatch(method, md))
                    .findFirst();

            if (opt.isPresent()) {
                MethodDeclaration md = opt.get();
                node.filePath = sourceFile.toString();
                node.startLine = md.getBegin().map(p -> p.line).orElse(-1);
                node.sourceCode = md.toString();
            }
        } catch (Exception e) {
            System.err.println("❌ 匹配失败: " + method.getSignature());
            e.printStackTrace();
        }

        methodMap.put(methodId, node);
    }

    private static boolean looseMatch(SootMethod method, MethodDeclaration md) {
        if (!md.getNameAsString().equals(method.getName())) return false;

        List<String> sootParams = getSimpleSootParamTypes(method);
        List<String> parserParams = getJavaParserParamTypes(md);

        return sootParams.equals(parserParams);
    }

    private static List<String> getJavaParserParamTypes(MethodDeclaration md) {
        return md.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.toList());
    }

    private static List<String> getSimpleSootParamTypes(SootMethod method) {
        return method.getParameterTypes().stream()
                .map(type -> {
                    String full = type.toString();
                    int lastDot = full.lastIndexOf('.');
                    return lastDot >= 0 ? full.substring(lastDot + 1) : full;
                })
                .collect(Collectors.toList());
    }

    private static String getMethodId(SootMethod method) {
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        String params = method.getParameterTypes().stream()
                .map(t -> {
                    String full = t.toString();
                    int idx = full.lastIndexOf('.');
                    return idx != -1 ? full.substring(idx + 1) : full;
                })
                .collect(Collectors.joining(","));
        return className + "#" + methodName + "(" + params + ")";
    }

    public static String queryCallGraph(String jsonPath, String filePath, String methodName, int lineNumber) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        CallGraphJson graph;

        List<String> callers = new ArrayList<>();
        List<String> callees = new ArrayList<>();
        Map<String, String> methodIdToSourceCode = new HashMap<>();
        String targetId = null;

        try (FileReader reader = new FileReader(jsonPath)) {
            graph = gson.fromJson(reader, CallGraphJson.class);
        } catch (Exception e) {
//            System.err.println("❌ 读取 JSON 文件失败: " + jsonPath);
            e.printStackTrace();
            return gson.toJson(Map.of("error", "failed to read json"));
        }

        // 查找方法 ID
        for (MethodNode node : graph.nodes) {
            if (node.filePath != null && node.filePath.equals(filePath)
                    && node.methodName.equals(methodName)
                    && node.startLine == lineNumber) {
                targetId = node.methodId;
                break;
            }
        }

        if (targetId == null) {
//            System.out.println("❌ 未找到方法: " + filePath + "@" + methodName + ":" + lineNumber);
            return gson.toJson(Map.of("error", "method not found"));
        }

        // 收集 caller / callee
        for (EdgeRecord e : graph.edges) {
            if (e.tgt.equals(targetId)) {
                callers.add(e.src);
            }
            if (e.src.equals(targetId)) {
                callees.add(e.tgt);
            }
        }

        // 收集相关源码
        Set<String> allRelated = new HashSet<>();
        allRelated.add(targetId);
        allRelated.addAll(callers);
        allRelated.addAll(callees);

        for (MethodNode node : graph.nodes) {
            if (allRelated.contains(node.methodId)) {
                methodIdToSourceCode.put(node.methodId, node.sourceCode);
            }
        }

        // 构建 JSON 返回结构
        Map<String, Object> result = new HashMap<>();
        result.put("methodId", targetId);
        result.put("callers", callers);
        result.put("callees", callees);
        result.put("methodSourceMap", methodIdToSourceCode);

        return gson.toJson(result);
    }

}