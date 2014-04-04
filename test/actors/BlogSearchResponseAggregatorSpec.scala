/*
* (c) 2014, Gribkov Yury
* http://linkedin.com/in/ygribkov/
*/
package actors

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe, TestKit}
import akka.actor.{Terminated, ActorSystem}
import scala.concurrent.duration._
import java.net.URI

@RunWith(classOf[JUnitRunner])
class BlogSearchResponseAggregatorSpec extends Specification {

  import BlogSearchResponseAggregator._

  "BlogSearchResponseAggregator" should {
    "request links for each keyword, returns aggregated result and terminates" in {
      val f = new BlogSearchResponseAggregatorFixture(500 milli) {
        val k1 = "k1"
        val k2 = "k2"
        val l1 = URI.create("http://domain.ru/link1")
        val l2 = URI.create("http://domain.ru/link2")
      }
      import f._

      aggregator ! Request(Set(k1, k2))
      prob expectMsgAllOf (
        BlogSearcher.Search(k1),
        BlogSearcher.Search(k2)
      )
      prob reply BlogSearcher.Found(Seq(l1))
      prob reply BlogSearcher.Found(Seq(l2))
      expectMsg(AggregatedResult(Set(l1, l2)))

      watch(aggregator)
      expectMsgType[Terminated].getActor === aggregator

      system.shutdown()
      success
    }

    "terminate after timeout" in {
      val f = new BlogSearchResponseAggregatorFixture(500 milli)
      import f._

      aggregator ! Request(Set())
      watch(aggregator)

      within(500 milli, 1000 milli) {
        expectMsgType[Terminated].getActor === aggregator
      }

      system.shutdown()
      success
    }
  }
}

class BlogSearchResponseAggregatorFixture(timeout: Duration) extends TestKit(ActorSystem()) with ImplicitSender {

  val prob = TestProbe()
  val aggregator = TestActorRef(BlogSearchResponseAggregator(prob.ref, timeout))
}