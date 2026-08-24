package dev.khbd.interp4j.intellij.refactoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import dev.khbd.interp4j.intellij.BaseIntellijTest;
import org.junit.jupiter.api.Test;

/**
 * @author Sergei_Khadanovich
 */
public class InterpolationIntroduceRefactoringSupportProviderTest extends BaseIntellijTest {

    private final InterpolationIntroduceRefactoringSupportProvider provider =
            new InterpolationIntroduceRefactoringSupportProvider();

    @Test
    public void isAvailable_expressionInsideInterpolation_trueAndHandlersDisabled() {
        String source = """
                package oap.application;

                import static dev.khbd.interp4j.core.Interpolations.s;

                public class Main {
                    public String a;

                    public void test() {
                        System.out.println(s("Hello World! - ${a}/${java.nio.charset.StandardCharsets.UTF_8}"));
                    }
                }
                """;
        PsiFile file = fixture.configureByText("Main.java", source);
        int offset = source.indexOf("UTF_8");

        boolean available = ReadAction.compute(() -> provider.isAvailable(elementAt(file, offset)));

        assertThat(available).isTrue();
        assertThat(provider.getIntroduceVariableHandler()).isNull();
        assertThat(provider.getIntroduceVariableHandler(null)).isNull();
        assertThat(provider.getExtractMethodHandler()).isNull();
        assertThat(provider.getIntroduceConstantHandler()).isNull();
        assertThat(provider.getIntroduceFieldHandler()).isNull();
        assertThat(provider.getIntroduceParameterHandler()).isNull();
    }

    @Test
    public void isAvailable_expressionOutsideInterpolation_false() {
        String source = """
                package oap.application;

                public class Main {
                    public Object a = java.nio.charset.StandardCharsets.UTF_8;
                }
                """;
        PsiFile file = fixture.configureByText("Main.java", source);
        int offset = source.indexOf("UTF_8");

        boolean available = ReadAction.compute(() -> provider.isAvailable(file.findElementAt(offset)));

        assertThat(available).isFalse();
    }

    private PsiElement elementAt(PsiFile hostFile, int hostOffset) {
        PsiElement injected = InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(hostFile, hostOffset);
        return injected != null ? injected : hostFile.findElementAt(hostOffset);
    }
}
