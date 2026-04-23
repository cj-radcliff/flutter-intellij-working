package io.flutter.tests.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.steps.CommonSteps
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.Test
import org.junit.runner.RunWith
import com.jetbrains.test.robot.path
import com.jetbrains.test.robot.remote.robot.RemoteRobotWrapper
import com.jetbrains.test.robot.runner.RobotTestRunner
import java.time.Duration

@RunWith(RobotTestRunner::class)
class MyTest {
    private val remoteRobot = RemoteRobotWrapper((RobotTestRunner.getRemoteRobot()))

    @Test
    fun myTest() {
        // Your test code here
    }
}
