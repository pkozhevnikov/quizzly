package quizzly.author

import akka.actor.typed.ActorSystem
import com.typesafe.config.Config
import com.zaxxer.hikari.HikariDataSource
import scalikejdbc.*
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location

object ScalikeJdbcSetup:

  def apply(system: ActorSystem[?]): Unit =
    initFromConfig(system.name, system.settings.config.getConfig("jdbc-connection-settings"))
    system
      .whenTerminated
      .map { _ =>
        ConnectionPool.close(system.name)
      }(using scala.concurrent.ExecutionContext.Implicits.global)

  private def initFromConfig(name: String, config: Config): Unit =

    val dataSource = buildDataSource(name, config)

    ConnectionPool.add(
      name,
      DataSourceConnectionPool(dataSource = dataSource, closer = HikariCloser(dataSource))
    )

    import scala.collection.JavaConverters.*

    if config.getBoolean("migration") then
      Flyway
        .configure
        .dataSource(dataSource)
        .table(config.getString("migrations-table"))
        .locations(config.getStringList("migrations-locations").asScala.map(Location(_)).toSeq: _*)
        .group(true)
        .outOfOrder(false)
        .load
        .migrate

  private def buildDataSource(name: String, config: Config): HikariDataSource =
    val dataSource = HikariDataSource()

    dataSource.setPoolName(name)
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
