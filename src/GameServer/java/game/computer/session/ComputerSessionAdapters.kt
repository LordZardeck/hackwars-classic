package game.computer.session

import util.sql
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.FileReader
import java.net.URL
import javax.imageio.ImageIO

interface SqlSession {
    fun query(command: String): List<String>?
    fun update(command: String)
    fun close()
}

interface SqlSessionFactory {
    fun open(connection: String, database: String, username: String, password: String): SqlSession
}

class DefaultSqlSessionFactory : SqlSessionFactory {
    override fun open(connection: String, database: String, username: String, password: String): SqlSession {
        return DefaultSqlSession(sql(connection, database, username, password))
    }
}

private class DefaultSqlSession(
    private val delegate: sql,
) : SqlSession {
    override fun query(command: String): List<String>? {
        val result = delegate.process(command) ?: return null
        return result.mapNotNull { it as? String }
    }

    override fun update(command: String) {
        delegate.process(command)
    }

    override fun close() {
        delegate.close()
    }
}

interface XmlRpcGateway {
    fun execute(url: String, method: String, params: Array<Any?>): Any?
}

class DefaultXmlRpcGateway : XmlRpcGateway {
    override fun execute(url: String, method: String, params: Array<Any?>): Any? {
        return util.XmlRpcProxy.execute(url, method, params)
    }
}

interface PasswordSource {
    fun readPassword(): String?
}

class FilePasswordSource(
    private val passwordFilePath: String,
) : PasswordSource {
    override fun readPassword(): String? {
        return try {
            BufferedReader(FileReader(passwordFilePath)).use { reader ->
                reader.readLine()
            }
        } catch (_: Exception) {
            null
        }
    }
}

interface CaptchaImageSource {
    fun load(url: String): BufferedImage
}

class DefaultCaptchaImageSource : CaptchaImageSource {
    override fun load(url: String): BufferedImage {
        return ImageIO.read(URL(url))
    }
}

interface CaptchaKeyGenerator {
    fun generate(length: Int): String
}

class RandomCaptchaKeyGenerator : CaptchaKeyGenerator {
    override fun generate(length: Int): String {
        val builder = StringBuilder(length)
        repeat(length) {
            builder.append(('0'.code + (Math.random() * 10).toInt()).toChar())
        }
        return builder.toString()
    }
}
