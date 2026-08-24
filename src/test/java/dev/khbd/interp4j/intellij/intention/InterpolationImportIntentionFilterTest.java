package dev.khbd.interp4j.intellij.intention;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import dev.khbd.interp4j.intellij.BaseIntellijTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * @author Sergei_Khadanovich
 */
public class InterpolationImportIntentionFilterTest extends BaseIntellijTest {

    private final InterpolationImportIntentionFilter filter = new InterpolationImportIntentionFilter();

    @Test
    public void accept_addStaticImportInsideInterpolation_rejected() {
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

        boolean accepted = ReadAction.compute(() -> filter.accept(addStaticImportIntention(), file, offset));

        assertThat(accepted).isFalse();
    }

    @Test
    public void accept_addStaticImportOutsideInterpolation_allowed() {
        String source = """
                package oap.application;

                public class Main {
                    public Object a = java.nio.charset.StandardCharsets.UTF_8;
                }
                """;
        PsiFile file = fixture.configureByText("Main.java", source);
        int offset = source.indexOf("UTF_8");

        boolean accepted = ReadAction.compute(() -> filter.accept(addStaticImportIntention(), file, offset));

        assertThat(accepted).isTrue();
    }

    @Test
    public void accept_unrelatedIntentionInsideInterpolation_allowed() {
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

        boolean accepted = ReadAction.compute(() -> filter.accept(unrelatedIntention(), file, offset));

        assertThat(accepted).isTrue();
    }

    @Test
    public void accept_unknownOffset_allowed() {
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

        boolean accepted = ReadAction.compute(() -> filter.accept(addStaticImportIntention(), file));

        assertThat(accepted).isTrue();
    }

    private IntentionAction addStaticImportIntention() {
        return fakeIntention("Add static import for 'java.nio.charset.StandardCharsets.UTF_8'");
    }

    private IntentionAction unrelatedIntention() {
        return fakeIntention("Some unrelated intention");
    }

    private IntentionAction fakeIntention(String familyName) {
        return new IntentionAction() {
            @Override
            public @NotNull String getText() {
                return familyName;
            }

            @Override
            public @NotNull String getFamilyName() {
                return familyName;
            }

            @Override
            public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
                return true;
            }

            @Override
            public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
            }

            @Override
            public boolean startInWriteAction() {
                return false;
            }
        };
    }
}
