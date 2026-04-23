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
                    
                    // Assert that the Flutter SDK text box is present and not empty.
                    Assertions.assertTrue(sdkPathTextBox.present(), "Flutter SDK path text box should be visible")
                    val texts = sdkPathTextBox.getAllTexts()
                    Assertions.assertTrue(texts.isNotEmpty() && texts.first().text.isNotBlank(), "Flutter SDK path should not be blank")

                    // 4. Select Next.
                    nextButton.click()

                    // Assert that the new project dialog is open and contains the expected fields.
                    Assertions.assertTrue(projectNameLabel.present(), "Project name field should be visible")
                    Assertions.assertTrue(projectLocationLabel.present(), "Project location field should be visible")

                    // 5. Name the project.
                    projectNameInput.click()
                    keyboard {
                        typeText("my_flutter_app")
                    }
                }
            }
            assert(false) { "This test is expected to fail and serve as a placeholder for UI interaction implementation." }
        }
    }
}
