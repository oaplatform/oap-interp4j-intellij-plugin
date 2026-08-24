package dev.khbd.interp4j.intellij.refactoring;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.refactoring.RefactoringActionHandler;
import dev.khbd.interp4j.intellij.common.Interp4jPsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Disables "Introduce Variable"/"Introduce Constant"/"Introduce Field"/"Introduce
 * Parameter"/"Extract Method" inside {@code ${...}} interpolation expressions.
 *
 * <p>{@code ${...}} is injected as Java into a synthetic single-expression file (see
 * {@code AbstractInterpolationLanguageInjector}) with no statement list anywhere a
 * {@code Type name = expr;} declaration could be inserted before the usage — these
 * refactorings need exactly that, so instead of erroring they silently rename the usage
 * without ever inserting the declaration, leaving broken code behind.
 *
 * <p>{@link com.intellij.lang.LanguageRefactoringSupport#forContext(PsiElement)} returns the
 * *first* registered {@link RefactoringSupportProvider} (for the element's language) whose
 * {@link #isAvailable(PsiElement)} is {@code true}, so this is registered with
 * {@code order="first"} in {@code plugin.xml}: it claims {@code isAvailable() == true} only
 * for elements inside our interpolation injection (where every handler getter below returns
 * {@code null}, same as the base class default — this is what actually disables the
 * refactorings), and {@code false} everywhere else, deferring to Java's real provider.
 *
 * @author Sergei_Khadanovich
 */
public class InterpolationIntroduceRefactoringSupportProvider extends RefactoringSupportProvider {

    @Override
    public boolean isAvailable(@NotNull PsiElement element) {
        PsiLiteralExpression literal = resolveInterpolationLiteral(element);
        if (literal == null) {
            return false;
        }

        return Interp4jPsiUtil.isPartOfInterpolationCall(literal,
                call -> Interp4jPsiUtil.isSMethodCall(call) || Interp4jPsiUtil.isFmtMethodCall(call));
    }

    private PsiLiteralExpression resolveInterpolationLiteral(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (Objects.nonNull(containingFile)) {
            PsiElement host = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(containingFile);
            if (host instanceof PsiLiteralExpression literal) {
                return literal;
            }
        }
        if (element instanceof PsiLiteralExpression literal) {
            return literal;
        }
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceVariableHandler() {
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceVariableHandler(PsiElement element) {
        return null;
    }

    @Override
    public RefactoringActionHandler getExtractMethodHandler() {
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceConstantHandler() {
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceFieldHandler() {
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceParameterHandler() {
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceFunctionalParameterHandler() {
        return null;
    }

    @Override
    public RefactoringActionHandler getIntroduceFunctionalVariableHandler() {
        return null;
    }
}
