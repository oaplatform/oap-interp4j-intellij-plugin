package dev.khbd.interp4j.intellij.optimizer;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyadicExpression;
import dev.khbd.interp4j.intellij.common.Interp4jPsiUtil;
import dev.khbd.interp4j.intellij.common.grammar.fmt.FmtCode;
import dev.khbd.interp4j.intellij.common.grammar.fmt.FmtExpressionParser;
import dev.khbd.interp4j.intellij.common.grammar.fmt.FmtExpressionVisitor;
import dev.khbd.interp4j.intellij.common.grammar.s.SCode;
import dev.khbd.interp4j.intellij.common.grammar.s.SExpressionParser;
import dev.khbd.interp4j.intellij.common.grammar.s.SExpressionVisitor;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects the set of simple identifier names referenced inside {@code s()}/{@code fmt()}
 * interpolation expressions ({@code ${...}}) in a Java file.
 *
 * <p>Used by {@link InterpolationImportOptimizer} to restore an import Optimize Imports
 * would otherwise drop.
 *
 * @author Sergei_Khadanovich
 */
@UtilityClass
public class InterpolationReferenceCollector {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /**
     * Collect every identifier token referenced inside a {@code ${...}} interpolation
     * expression anywhere in the file.
     *
     * @param file java file
     * @return referenced identifier names
     */
    public static Set<String> collectNeededNames(PsiJavaFile file) {
        Set<String> names = new HashSet<>();
        file.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);

                boolean isS = Interp4jPsiUtil.isSMethodCall(call);
                boolean isFmt = !isS && Interp4jPsiUtil.isFmtMethodCall(call);
                if (!isS && !isFmt) {
                    return;
                }

                PsiExpressionList arguments = call.getArgumentList();
                if (arguments.isEmpty()) {
                    return;
                }

                collectFromExpression(arguments.getExpressions()[0], isS, names);
            }
        });
        return names;
    }

    private static void collectFromExpression(PsiExpression expression, boolean isS, Set<String> names) {
        if (expression instanceof PsiLiteralExpression literal) {
            collectFromLiteral(literal, isS, names);
        } else if (expression instanceof PsiPolyadicExpression poly) {
            for (PsiExpression operand : poly.getOperands()) {
                if (operand instanceof PsiLiteralExpression literal) {
                    collectFromLiteral(literal, isS, names);
                }
            }
        }
    }

    private static void collectFromLiteral(PsiLiteralExpression literal, boolean isS, Set<String> names) {
        String text = Interp4jPsiUtil.getStringLiteralText(literal);
        if (Objects.isNull(text)) {
            return;
        }

        if (isS) {
            SExpressionParser.getInstance().parse(text)
                    .ifPresent(expr -> expr.visit(new SExpressionVisitor() {
                        @Override
                        public void visitCode(SCode code) {
                            addTokens(code.expression(), names);
                        }
                    }));
        } else {
            FmtExpressionParser.getInstance().parse(text)
                    .ifPresent(expr -> expr.visit(new FmtExpressionVisitor() {
                        @Override
                        public void visitCodePart(FmtCode code) {
                            addTokens(code.expression(), names);
                        }
                    }));
        }
    }

    private static void addTokens(String expression, Set<String> names) {
        Matcher matcher = IDENTIFIER.matcher(expression);
        while (matcher.find()) {
            names.add(matcher.group());
        }
    }
}
