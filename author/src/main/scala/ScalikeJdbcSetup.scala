package quizzly.author

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import scalikejdbc.*

object ScalikeJdbcSetup:

  def apply(system: ActorSystem[_]): Unit =
    initFromConfig(system.name, system.settings.config)
    system
      .whenTerminated
      .map { _ =>
        ConnectionPool.close(system.name)
      }(using scala.concurrent.ExecutionContext.Implicits.global)

  private def initFromConfig(name: String, config: Config): Unit =

    val dataSource = buildDataSource(config.getConfig("jdbc-connection-settings"))

    ConnectionPool.add(
      name,
      DataSourceConnectionPool(dataSource = dataSource, closer = HikariCloser(dataSource))
    )

  private def buildDataSource(config: Config): HikariDataSource =
    val dataSource = HikariDataSource()

    dataSource.setPoolName("rscp")
    dataSource.setMaximumPoolSize(config.getInt("connection-pool.max-pool-size"))

    val timeout = config.getDuration("connection-pool.timeout").toMillis
    dataSource.setConnectionTimeout(timeout)

    dataSource.setDriverClassName(config.getString("driver"))
    dataSource.setJdbcUrl(config.getString("url"))
    dataSource.setUsername(config.getString("user"))
    dataSource.setPassword(config.getString("password"))

    dataSource

  private case class HikariCloser(dataSource: HikariDataSource) extends DataSourceCloser:
    override def close(): Unit = dataSource.close()
