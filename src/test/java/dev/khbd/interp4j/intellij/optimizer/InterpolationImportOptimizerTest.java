package dev.khbd.interp4j.intellij.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import dev.khbd.interp4j.intellij.BaseIntellijTest;
import org.junit.jupiter.api.Test;

/**
 * @author Sergei_Khadanovich
 */
public class InterpolationImportOptimizerTest extends BaseIntellijTest {

    @Test
    public void optimizeImports_staticImportUsedOnlyInInterpolation_keepsImport() {
        PsiFile file = fixture.configureByText("Main.java", """
                package oap.application;

                import java.util.List;
                import static java.nio.charset.StandardCharsets.UTF_8;
                import static dev.khbd.interp4j.core.Interpolations.s;

                public class Main {
                    public String a;

                    public void test() {
                        System.out.println(s("Hello World! - ${a}/${UTF_8}"));
                    }
                }
                """);

        optimizeImports(file);

        assertThat(file.getText())
                .contains("import static java.nio.charset.StandardCharsets.UTF_8;")
                .doesNotContain("import java.util.List;");
    }

    @Test
    public void optimizeImports_classImportUsedOnlyInInterpolation_keepsImport() {
        PsiFile file = fixture.configureByText("Main.java", """
                package oap.application;

                import java.nio.charset.StandardCharsets;
                import static dev.khbd.interp4j.core.Interpolations.s;

                public class Main {
                    public void test() {
                        System.out.println(s("Charset - ${StandardCharsets.UTF_8}"));
                    }
                }
                """);

        optimizeImports(file);

        assertThat(file.getText())
                .contains("import java.nio.charset.StandardCharsets;");
    }

    @Test
    public void optimizeImports_classImportUsedViaBareStaticMemberInInterpolation_keepsImportAndRemovesDecoy() {
        PsiFile file = fixture.configureByText("Main.java", """
                package oap.application;

                import java.nio.charset.Charset;
                import java.nio.charset.StandardCharsets;
                import static dev.khbd.interp4j.core.Interpolations.s;

                public class Main {
                    public String a;

                    public void test() {
                        System.out.println(s("Hello World! - ${a}/${UTF_8}"));
                    }
                }
                """);

        optimizeImports(file);

        assertThat(file.getText())
                .contains("import java.nio.charset.StandardCharsets;")
                .doesNotContain("import java.nio.charset.Charset;\n");
    }

    @Test
    public void optimizeImports_classImportRedundantWithExplicitStaticImport_removesClassImportKeepsStaticImport() {
        PsiFile file = fixture.configureByText("Main.java", """
                package oap.application;

                import java.nio.charset.StandardCharsets;
                import static dev.khbd.interp4j.core.Interpolations.s;
                import static java.nio.charset.StandardCharsets.UTF_8;

                public class Main {
                    public String a;

                    public void test() {
                        System.out.println(s("Hello World! - ${a}/${UTF_8}"));
                    }
                }
                """);

        optimizeImports(file);

        assertThat(file.getText())
                .contains("import static java.nio.charset.StandardCharsets.UTF_8;")
                .doesNotContain("import java.nio.charset.StandardCharsets;\n");
    }

    @Test
    public void optimizeImports_importNotUsedAnywhere_removesImport() {
        PsiFile file = fixture.configureByText("Main.java", """
                package oap.application;

                import java.util.List;
                import static dev.khbd.interp4j.core.Interpolations.s;

                public class Main {
                    public String a;

                    public void test() {
                        System.out.println(s("Hello World! - ${a}"));
                    }
                }
                """);

        optimizeImports(file);

        assertThat(file.getText()).doesNotContain("import java.util.List;");
    }

    private void optimizeImports(PsiFile file) {
        ApplicationManager.getApplication()
                .invokeAndWait(() -> new OptimizeImportsProcessor(getProject(), file).run());
    }
}
