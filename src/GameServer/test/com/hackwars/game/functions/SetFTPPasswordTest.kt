package com.hackwars.game.functions

import game.ApplicationData
import org.junit.Test
import org.mockito.kotlin.verify

class SetFTPPasswordTest {
    @Test
    fun execute_updatesPassword() {
        val computer = FunctionTestSupport.baseComputer()

        SetFTPPassword(computer).execute(FunctionTestSupport.stringCommand("setftppassword", "pw123"))

        verify(computer).setPassword("pw123")
    }
}
