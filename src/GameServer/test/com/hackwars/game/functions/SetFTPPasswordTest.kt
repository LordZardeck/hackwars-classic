package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class SetFTPPasswordTest {
    @Test
    fun execute_updatesPassword() {
        val computer = FunctionTestSupport.baseComputer()

        SetFTPPassword(computer).execute(ApplicationData("setftppassword", "pw123", 0, "source"))

        verify(computer).setPassword("pw123")
    }
}
