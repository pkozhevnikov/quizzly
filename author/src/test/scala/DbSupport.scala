package quizzly.author

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

trait DbSupport {

  import DbSupport._

  def startDb() = {
    val config = new HikariConfig()
    val name = java.util.UUID.randomUUID.toString
    config.setJdbcUrl(s"jdbc:h2:mem:$name")
    config.setUsername("sa")
    config.setPassword("sa")
    config.setMaximumPoolSize(10)
    config.addDataSourceProperty("allowMultiQueries", "true")
    config.setDriverClassName(
      "org.h2.Driver"
    ) // fork = true in build.sbt doesn't help beat "no suitable driver"
    ds = new HikariDataSource(config)
    runScript("db.ddl")
  }

  def runScript(path: String): Unit = {
    val conn = ds.getConnection()
    try conn.createStatement().executeUpdate(readScript(path))
    finally conn.close()
  }

  def stopDb() = ds.close()

  private var ds: HikariDataSource = null
  def db = ds

}

object DbSupport {

  def readScript(path: String): String = {
    val src = scala.io.Source.fromResource(path)
    try src.mkString
    finally src.close()
  }

}
