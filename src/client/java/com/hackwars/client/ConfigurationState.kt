package com.hackwars.client

object ConfigurationState {
    object GameServer {
        val Address: String = System.getProperty("hackwars.gameServer.address", "127.0.0.1")
        val InPort = System.getProperty("hackwars.gameServer.inPort", "10021").toInt()
        val OutPort = System.getProperty("hackwars.gameServer.outPort", "10020").toInt()
    }

    object ChatServer {
        val Address: String = System.getProperty("hackwars.chatServer.address", "127.0.0.1")
        val InPort = System.getProperty("hackwars.chatServer.inPort", "10026").toInt()
        val OutPort = System.getProperty("hackwars.chatServer.outPort", "10025").toInt()
    }

    object XMLRPCServer {
        val Address: String = System.getProperty("hackwars.xmlrpcServer.address", "127.0.0.1")
    }
}