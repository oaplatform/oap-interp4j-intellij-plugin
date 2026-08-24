package dev.khbd.interp4j.intellij.language;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import dev.khbd.interp4j.intellij.common.Interp4jPsiUtil;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Sergei_Khadanovich
 */
@RequiredArgsConstructor
public abstract class AbstractInterpolationLanguageInjector implements MultiHostInjector {

    private static final String PREFIX = "public class Main { public static Object __target__ = ";
    private static final String SUFFIX = "; }";

    private final Predicate<PsiMethodCallExpression> callPredicate;

    @Override
    public void getLanguagesToInject(MultiHostRegistrar registrar, PsiElement context) {
        PsiLiteralExpression literalExpr = (PsiLiteralExpression) context;

        String value = Interp4jPsiUtil.getStringLiteralText(literalExpr);
        if (Objects.isNull(value)) {
            return;
        }

        if (isInterpolateMethodCall(literalExpr)) {
            injectLanguage(registrar, literalExpr, value);
        }
    }

    /**
     * Check if method call is interpolate method call.
     *
     * @param literal string literal
     */
    protected boolean isInterpolateMethodCall(PsiLiteralExpression literal) {
        return Interp4jPsiUtil.isPartOfInterpolationCall(literal, callPredicate);
    }

    /**
     * Inject language into literal expression.
     */
    abstract protected void injectLanguage(MultiHostRegistrar registrar,
                                           PsiLiteralExpression context,
                                           String value);

    @RequiredArgsConstructor
    protected static class InjectionVisitor {

        private final MultiHostRegistrar registrar;
        private final PsiLiteralExpression context;

        /**
         * Inject code into context.
         */
        protected void inject(int start, int end) {
            TextRange range = new TextRange(start, end);
            registrar.startInjecting(JavaLanguage.INSTANCE);
            registrar.addPlace(PREFIX, SUFFIX, (PsiLanguageInjectionHost) context, range);
            registrar.doneInjecting();
        }
    }

    @Override
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(PsiLiteralExpression.class);
    }
}
