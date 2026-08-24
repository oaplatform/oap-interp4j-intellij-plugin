package dev.khbd.interp4j.intellij.optimizer;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import dev.khbd.interp4j.intellij.common.Interp4jPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Runs Java's own import optimization and then restores any import which is referenced
 * only inside {@code s()}/{@code fmt()} interpolation expressions.
 *
 * <p>Interpolation code (the {@code ${...}} parts) is injected into a synthetic,
 * host-import-free PSI tree (see {@code AbstractInterpolationLanguageInjector}), so the
 * default Java import optimizer never sees such references and drops the corresponding
 * import as unused.
 *
 * <p>This class does the default optimization itself (via
 * {@link JavaCodeStyleManager#prepareOptimizeImportsResult(PsiJavaFile)}, the same API
 * {@code com.intellij.lang.java.JavaImportOptimizer} uses) rather than registering
 * alongside it and relying on both running: {@code LanguageImportStatements.forFile}
 * (the platform code backing "Optimize Imports") stops at the first {@link ImportOptimizer}
 * whose {@link #supports(PsiFile)} matches a given file, so a second, independently
 * registered optimizer for the same language never actually runs. Matching (and narrowing)
 * {@link #supports(PsiFile)} to files containing an interpolation call makes this optimizer
 * the sole one selected for those files, while leaving every other Java file to the
 * platform's own optimizer.
 *
 * @author Sergei_Khadanovich
 */
public class InterpolationImportOptimizer implements ImportOptimizer {

    @Override
    public boolean supports(@NotNull PsiFile file) {
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        String text = file.getText();
        return text.contains(Interp4jPsiUtil.S + "(") || text.contains(Interp4jPsiUtil.FMT + "(");
    }

    @Override
    public @NotNull Runnable processFile(@NotNull PsiFile file) {
        PsiJavaFile javaFile = (PsiJavaFile) file;
        Project project = javaFile.getProject();

        Set<String> neededNames = InterpolationReferenceCollector.collectNeededNames(javaFile);
        List<ImportSnapshot> snapshots = neededNames.isEmpty()
                ? List.of()
                : collectMatchingImports(javaFile, neededNames);

        PsiImportList preparedResult = JavaCodeStyleManager.getInstance(project).prepareOptimizeImportsResult(javaFile);
        if (Objects.isNull(preparedResult)) {
            return () -> {
            };
        }

        return () -> applyResult(project, javaFile, preparedResult, snapshots);
    }

    private void applyResult(Project project, PsiJavaFile javaFile, PsiImportList preparedResult,
                             List<ImportSnapshot> snapshots) {
        if (!snapshots.isEmpty()) {
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            for (ImportSnapshot snapshot : snapshots) {
                if (isAlreadyImported(preparedResult, snapshot)) {
                    continue;
                }

                PsiClass targetClass = JavaPsiFacade.getInstance(project)
                        .findClass(snapshot.qualifiedClassName, GlobalSearchScope.allScope(project));
                if (Objects.isNull(targetClass)) {
                    continue;
                }

                PsiImportStatementBase newImport = Objects.isNull(snapshot.staticMemberName)
                        ? factory.createImportStatement(targetClass)
                        : factory.createImportStaticStatement(targetClass, snapshot.staticMemberName);
                preparedResult.add(newImport);
            }
        }

        javaFile.getImportList().replace(preparedResult);
    }

    private List<ImportSnapshot> collectMatchingImports(PsiJavaFile file, Set<String> neededNames) {
        List<ImportSnapshot> snapshots = new ArrayList<>();

        PsiImportList importList = file.getImportList();
        if (Objects.isNull(importList)) {
            return snapshots;
        }

        // an explicit static import (e.g. "import static ...StandardCharsets.UTF_8;") already
        // satisfies that bare name on its own, so it takes precedence over the "bare reference
        // to one of a class import's own static members" fallback below — otherwise a plain
        // "import java.nio.charset.StandardCharsets;" sitting alongside it would be wrongly
        // treated as needed too, for the exact same reference.
        Set<String> staticallyImportedNames = new HashSet<>();
        for (PsiImportStatementBase importStatement : importList.getAllImportStatements()) {
            if (importStatement instanceof PsiImportStaticStatement staticImport && !staticImport.isOnDemand()) {
                String memberName = staticImport.getReferenceName();
                if (Objects.nonNull(memberName)) {
                    staticallyImportedNames.add(memberName);
                }
            }
        }

        for (PsiImportStatementBase importStatement : importList.getAllImportStatements()) {
            if (importStatement instanceof PsiImportStaticStatement staticImport) {
                String memberName = staticImport.getReferenceName();
                PsiClass targetClass = staticImport.resolveTargetClass();
                if (Objects.nonNull(memberName) && neededNames.contains(memberName) && Objects.nonNull(targetClass)) {
                    snapshots.add(new ImportSnapshot(targetClass.getQualifiedName(), memberName));
                }
            } else if (importStatement instanceof PsiImportStatement classImport
                       && classImport.resolve() instanceof PsiClass targetClass) {
                // matches either "${StandardCharsets.UTF_8}" (class name referenced directly)
                // or "${UTF_8}" (a bare reference to one of the class's own static members,
                // the way interp4j resolves an unqualified name against any imported class) —
                // excluding names already covered by an explicit static import
                boolean neededByOwnName = neededNames.contains(targetClass.getName());
                boolean neededByStaticMember = hasNeededStaticMember(targetClass, neededNames, staticallyImportedNames);
                if (neededByOwnName || neededByStaticMember) {
                    snapshots.add(new ImportSnapshot(targetClass.getQualifiedName(), null));
                }
            }
        }

        return snapshots;
    }

    private boolean hasNeededStaticMember(PsiClass targetClass, Set<String> neededNames,
                                          Set<String> staticallyImportedNames) {
        for (String name : neededNames) {
            if (staticallyImportedNames.contains(name)) {
                continue;
            }
            PsiField field = targetClass.findFieldByName(name, true);
            if (Objects.nonNull(field) && isPublicStatic(field)) {
                return true;
            }
            for (PsiMethod method : targetClass.findMethodsByName(name, true)) {
                if (isPublicStatic(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPublicStatic(PsiModifierListOwner member) {
        return member.hasModifierProperty(PsiModifier.STATIC) && member.hasModifierProperty(PsiModifier.PUBLIC);
    }

    private boolean isAlreadyImported(PsiImportList importList, ImportSnapshot snapshot) {
        for (PsiImportStatementBase importStatement : importList.getAllImportStatements()) {
            if (snapshot.staticMemberName == null) {
                if (importStatement instanceof PsiImportStatement classImport
                    && snapshot.qualifiedClassName.equals(classImport.getQualifiedName())) {
                    return true;
                }
            } else if (importStatement instanceof PsiImportStaticStatement staticImport
                       && snapshot.staticMemberName.equals(staticImport.getReferenceName())) {
                PsiClass targetClass = staticImport.resolveTargetClass();
                if (Objects.nonNull(targetClass) && snapshot.qualifiedClassName.equals(targetClass.getQualifiedName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private record ImportSnapshot(String qualifiedClassName, String staticMemberName) {
    }
}
