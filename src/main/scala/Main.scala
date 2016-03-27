import java.sql.ResultSet

import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.event.Logging
import akka.routing.RoundRobinPool
import com.zaxxer.hikari.{HikariDataSource, HikariConfig}
import slick.jdbc.JdbcBackend.Database
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}
import slick.driver.PostgresDriver.api._

import scala.util.Random


object Main extends App {
  println("Hello world")

  val system = ActorSystem("mySystem")
  val actor = system.actorOf(Props[Root])
//  actor ! "once"
//  actor ! "test"
  actor ! "round"
//  while (true) {}
//  actor ! KillYS
}

case class Hi(incr: Int, message: String = "Hi")
case object KillYS
class CustomException(m: String) extends Exception

class Root extends Actor {
  val log = Logging(context.system, this)
  import context._
  import context.dispatcher

  override val supervisorStrategy: SupervisorStrategy = {
    def defaultDecider: Decider = {
      case _: ActorInitializationException => Stop
      case _: ActorKilledException         => Stop
      case _: CustomException              => Escalate
      case _: Exception                    => Resume
    }
    OneForOneStrategy()(defaultDecider)
  }

  val child = actorOf(Props[Child])
  val routedChild = actorOf(RoundRobinPool(2).props(Props[Child]), "round-robin")
  var i = 0

  def receive = {
    case "answer" => log.info("Child answered")
    case "once" => child ! "Hi"
    case k @ KillYS => child ! k
    case "test" => system.scheduler.schedule(0.seconds, 10.milliseconds)(child ! "Hi")
    case "round" => system.scheduler.schedule(0.seconds, 1000.milliseconds){
      i = i + 1
      routedChild ! Hi(i)
    }
    case f: Future[Int] => f.foreach(i => log.info("Received " + i))
    case i: Int => log.info("Received " + i)
    case s: String => log.info("Received " + s)
    case _      => log.info("received unknown message")
  }
}

class Child extends Actor {
  import context._
  val log = Logging(system, this)
  var i = 1
  override def receive = {
    case "Hi" =>
      i = i + 1
      log.info("Hi " + i)

//      sender() ! data
//      sender() ! insertData
      sender() ! oldWay(i)
    case Hi(incr, message) =>
      log.info(message + " " + incr)
      sender() ! oldWay(incr)
//    case KillYS => throw new Exception("Killing yourself")
    case KillYS => throw new CustomException("Killing yourself")
  }

  def data = {
    val q = (for {
      _ <- sql"SELECT pg_sleep(5);".as[String] //TODO потестить с шедулом и с транзакционностью поиграть
      data <- sql"SELECT nextval('reference.devices_browsers_groups_id_seq');".as[Int].head
    } yield data).transactionally

    val res = Db.connection.run(q)
//    val res = Db.connection.run()
//    ss.close()
    res
  }

  def insertData = {
    val q = (for {
      data <- sqlu"INSERT INTO test2 VALUES ($i, 100500)"
      _ <- sqlu"INSERT INTO test VALUES (28881, 100500)"
    } yield data)
    Db.connection.run(q)
  }

  def oldWay(incr: Int) = {
    val session = Db.connection.createSession()
    val conn = session.conn
    conn.setAutoCommit(false)
    var res = 0

    try {
      conn.prepareStatement(s"INSERT INTO test2 VALUES ($incr, 100500)").executeUpdate()
      conn.prepareStatement("SELECT pg_sleep(5)").execute()
      if (Random.nextBoolean()) {
        throw new Exception("Between queries")
      }

      val rq = conn.prepareStatement(s"INSERT INTO test VALUES ($incr, 100500) returning id", ResultSet.TYPE_SCROLL_SENSITIVE,
        ResultSet.CONCUR_UPDATABLE).executeQuery()
      rq.first()
      res = rq.getInt("id")
      conn.commit()
    }catch {
      case e: org.postgresql.util.PSQLException =>
        log.error(e.getMessage)
        conn.rollback()
      case e: Exception =>
        log.error(e.getMessage)
        conn.rollback()
        throw e
    }finally {
//      conn.close()
      session.close()
    }
    res
  }

  override def postRestart(reason: Throwable) {
    super.postRestart(reason)
    log.info(s"Restarted because of ${reason.getMessage}")
  }

  override def postStop: Unit = {
    super.postStop
    log.info("Stopping")
  }
}

//TODO в планах посмотреть, как себя будет вести едулер на 2 сек и запрос на 10 сек, блокироки, потом надо будет посоздавать акторов через роутер  и в них делать блокирующие селекты, пул акторов > пула соединенй

object Db {
  private val dataSource = {
    val config = new HikariConfig()
    config.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")
    config.addDataSourceProperty("serverName", "192.168.1.107")
    config.addDataSourceProperty("user", "postgres")
    config.addDataSourceProperty("password", "")
    config.addDataSourceProperty("databaseName", "graphics")
    config.setMaximumPoolSize(5)
    new HikariDataSource(config)
  }

  private val db = Database.forDataSource(dataSource)

  def connection = db
}