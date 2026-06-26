package com.mcp.tools.index;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JavaSourceIndexer {

    @PostConstruct
    public void configureParser() {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        log.info("[JavaSourceIndexer] Parser configured with language level: JAVA_21");
    }

    public ParseResult parse(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath);
            String pkg = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            List<SymbolEntry> symbols = new ArrayList<>();
            Set<String> allImports = extractImports(cu);
            Set<String> allReferences = new HashSet<>();

            for (TypeDeclaration<?> type : cu.getTypes()) {
                SymbolEntry classEntry = extractTypeSymbol(type, pkg, filePath, allImports);
                symbols.add(classEntry);

                for (BodyDeclaration<?> member : type.getMembers()) {
                    if (member instanceof MethodDeclaration md) {
                        symbols.add(extractMethodSymbol(md, classEntry.getQualifiedName(), filePath));
                    } else if (member instanceof ConstructorDeclaration cd) {
                        symbols.add(extractConstructorSymbol(cd, classEntry.getQualifiedName(), filePath));
                    } else if (member instanceof FieldDeclaration fd) {
                        symbols.addAll(extractFieldSymbols(fd, classEntry.getQualifiedName(), filePath));
                    } else if (member instanceof TypeDeclaration<?> innerType) {
                        SymbolEntry innerEntry = extractTypeSymbol(innerType,
                                classEntry.getQualifiedName(), filePath, allImports);
                        symbols.add(innerEntry);
                    }
                }

                allReferences.addAll(extractMethodCalls(cu, classEntry.getQualifiedName()));
            }

            return new ParseResult(symbols, allReferences, allImports);
        } catch (IOException e) {
            log.warn("[Indexer] Failed to parse: {} - {}", filePath, e.getMessage());
            return ParseResult.empty();
        }
    }

    private SymbolEntry extractTypeSymbol(TypeDeclaration<?> type, String pkg,
                                          Path filePath, Set<String> imports) {
        String simpleName = type.getNameAsString();
        String fqn = pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
        SymbolKind kind = mapTypeKind(type);

        return SymbolEntry.builder()
                .name(simpleName)
                .qualifiedName(fqn)
                .kind(kind)
                .filePath(filePath.toString())
                .startLine(type.getBegin().map(p -> p.line).orElse(0))
                .endLine(type.getEnd().map(p -> p.line).orElse(0))
                .parentClass(pkg)
                .imports(new HashSet<>(imports))
                .annotations(extractAnnotationNames(type.getAnnotations()))
                .build();
    }

    private SymbolEntry extractMethodSymbol(MethodDeclaration md, String parentFqn,
                                            Path filePath) {
        String name = md.getNameAsString();
        String returnType = md.getTypeAsString();
        List<String> params = md.getParameters().stream()
                .map(p -> p.getTypeAsString())
                .collect(Collectors.toList());

        return SymbolEntry.builder()
                .name(name)
                .qualifiedName(parentFqn + "." + name)
                .kind(SymbolKind.METHOD)
                .filePath(filePath.toString())
                .startLine(md.getBegin().map(p -> p.line).orElse(0))
                .endLine(md.getEnd().map(p -> p.line).orElse(0))
                .parentClass(parentFqn)
                .returnType(returnType)
                .paramTypes(params)
                .annotations(extractAnnotationNames(md.getAnnotations()))
                .build();
    }

    private SymbolEntry extractConstructorSymbol(ConstructorDeclaration cd, String parentFqn,
                                                 Path filePath) {
        List<String> params = cd.getParameters().stream()
                .map(p -> p.getTypeAsString())
                .collect(Collectors.toList());

        return SymbolEntry.builder()
                .name(parentFqn.substring(parentFqn.lastIndexOf('.') + 1))
                .qualifiedName(parentFqn)
                .kind(SymbolKind.CONSTRUCTOR)
                .filePath(filePath.toString())
                .startLine(cd.getBegin().map(p -> p.line).orElse(0))
                .endLine(cd.getEnd().map(p -> p.line).orElse(0))
                .parentClass(parentFqn)
                .paramTypes(params)
                .annotations(extractAnnotationNames(cd.getAnnotations()))
                .build();
    }

    private List<SymbolEntry> extractFieldSymbols(FieldDeclaration fd, String parentFqn,
                                                  Path filePath) {
        String type = fd.getCommonType().asString();
        return fd.getVariables().stream()
                .map(v -> SymbolEntry.builder()
                        .name(v.getNameAsString())
                        .qualifiedName(parentFqn + "." + v.getNameAsString())
                        .kind(SymbolKind.FIELD)
                        .filePath(filePath.toString())
                        .startLine(fd.getBegin().map(p -> p.line).orElse(0))
                        .endLine(fd.getEnd().map(p -> p.line).orElse(0))
                        .parentClass(parentFqn)
                        .returnType(type)
                        .annotations(extractAnnotationNames(fd.getAnnotations()))
                        .build())
                .collect(Collectors.toList());
    }

    private Set<String> extractImports(CompilationUnit cu) {
        return cu.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .collect(Collectors.toSet());
    }

    private Set<String> extractMethodCalls(CompilationUnit cu, String classFqn) {
        Set<String> calls = new HashSet<>();
        cu.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getScope().isPresent()) {
                calls.add(mc.getScope().get() + "." + mc.getNameAsString());
            } else {
                calls.add(classFqn + "." + mc.getNameAsString());
            }
        });
        cu.findAll(ObjectCreationExpr.class).forEach(oc ->
                calls.add(oc.getTypeAsString()));
        return calls;
    }

    private List<String> extractAnnotationNames(List<?> annotations) {
        if (annotations == null || annotations.isEmpty()) return Collections.emptyList();
        return annotations.stream()
                .map(a -> {
                    if (a instanceof com.github.javaparser.ast.expr.AnnotationExpr ae) {
                        return ae.getNameAsString();
                    }
                    return a.toString();
                })
                .collect(Collectors.toList());
    }

    private SymbolKind mapTypeKind(TypeDeclaration<?> type) {
        if (type.isAnnotationDeclaration()) return SymbolKind.ANNOTATION;
        if (type instanceof EnumDeclaration) return SymbolKind.ENUM;
        if (type instanceof ClassOrInterfaceDeclaration ci) {
            if (ci.isInterface()) return SymbolKind.INTERFACE;
        }
        return SymbolKind.CLASS;
    }

    public record ParseResult(
            List<SymbolEntry> symbols,
            Set<String> references,
            Set<String> imports
    ) {
        public static ParseResult empty() {
            return new ParseResult(Collections.emptyList(), Collections.emptySet(), Collections.emptySet());
        }
    }
}