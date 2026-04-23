package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.newProjectDialog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("ui")
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class CreateNewFlutterMobileAppTest {

    private lateinit var run: BackgroundRun

    @BeforeEach
    fun setup() {
        run = Setup.setupTestContextIC(javaClass.simpleName).runIdeWithDriver()
    }

    @AfterEach
    fun tearDown() {
        if (::run.isInitialized) {
            run.closeIdeAndWait()
        }
    }

    @Test
    fun testCreateNewFlutterMobileAppWorkflow() {
        run.driver.withContext {
            // 1. Start IDE (handled by setupTestContextIC and UseLatestDownloadedIdeBuild)
            // No explicit action needed here as the context is already set up.

            // 2. In the menu, go to File -> New -> Project...
            //    This is now handled by the welcome screen interaction.

            // 3. In the New Project Dialog, select Flutter in the Generators list.
            welcomeScreen {
                createNewProjectButton.click()
                newProjectDialog {
                    chooseProjectType("Flutter")

                    // 4. Select Next.
                    nextButton.click()

                    // Assert that the new project dialog is open and contains the expected fields.
                    Assertions.assertTrue(x("//div[text()='Project name:']").present(), "Project name field should be visible")
                    Assertions.assertTrue(x("//div[text()='Project location:']").present(), "Project location field should be visible")
                }
            }
            assert(false) { "This test is expected to fail and serve as a placeholder for UI interaction implementation." }
        }
    }
}
