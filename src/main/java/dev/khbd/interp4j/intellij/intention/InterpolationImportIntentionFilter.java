package dev.khbd.interp4j.intellij.intention;

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import dev.khbd.interp4j.intellij.common.Interp4jPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Hides "add import"/"add static import" style intentions inside {@code ${...}}
 * interpolation expressions.
 *
 * <p>Interpolation code is injected as Java into a synthetic, single-expression file
 * (see {@code AbstractInterpolationLanguageInjector}) with no import list of its own, and
 * everything outside the actual {@code ${...}} text is a guarded (read-only) region. Any
 * intention that tries to insert an import there fails with "Action tries to modify guarded
 * region" instead of doing anything useful — and it's never actually needed, since a
 * fully-qualified reference inside {@code ${...}} already compiles as-is.
 *
 * <p>The platform always calls the three-arg {@link #accept(IntentionAction, PsiFile, int)}
 * with the *host* file (e.g. the real {@code .java} file being edited) and the caret/action
 * offset within it — never the injected fragment's own synthetic {@link PsiFile} directly —
 * so the injection at that offset has to be looked up via
 * {@link InjectedLanguageManager#findInjectedElementAt(PsiFile, int)}.
 *
 * @author Sergei_Khadanovich
 */
public class InterpolationImportIntentionFilter implements IntentionActionFilter {

    private static final String IMPORT_MARKER = "import";

    @Override
    public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file, int offset) {
        if (file == null || offset < 0 || !isImportIntention(intentionAction)) {
            return true;
        }

        PsiElement injectedElement = InjectedLanguageManager.getInstance(file.getProject())
                .findInjectedElementAt(file, offset);
        if (injectedElement == null) {
            return true;
        }

        PsiElement host = InjectedLanguageManager.getInstance(file.getProject())
                .getInjectionHost(injectedElement.getContainingFile());
        if (!(host instanceof PsiLiteralExpression literal)) {
            return true;
        }

        boolean isInterpolationLiteral = Interp4jPsiUtil.isPartOfInterpolationCall(literal,
                call -> Interp4jPsiUtil.isSMethodCall(call) || Interp4jPsiUtil.isFmtMethodCall(call));

        return !isInterpolationLiteral;
    }

    @Override
    public boolean accept(@NotNull IntentionAction intentionAction, @Nullable PsiFile file) {
        return accept(intentionAction, file, -1);
    }

    private boolean isImportIntention(IntentionAction action) {
        String familyName = action.getFamilyName();
        return familyName != null && familyName.toLowerCase(Locale.ROOT).contains(IMPORT_MARKER);
    }
}
