/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pushnotificationscheduler.actor

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.LoneElement
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.pushnotificationscheduler.actor.WorkPullingPattern._

import scala.concurrent.Future
import scala.concurrent.duration._

class WorkPullingPatternSpec extends TestKit(ActorSystem("AkkaWorkPullingSystem")) with LoneElement with ImplicitSender with UnitSpec {

  def newEpic[T](work: T) = Epic(List(work))

  def newEpic[T](work: Seq[T]) = Epic(work.toList)

  def newMaster[T] = TestActorRef[Master[T]](Props(new Master))

  def newMasterWithWorker[T] = {
    val master = newMaster
    master.underlyingActor.workers += TestProbe().ref
    master
  }

  def newWorker(master: ActorRef): ActorRef = TestActorRef[Worker[String]](Props(new Worker[String](master) {
    def doWork(work: String): Future[_] = Future {
      println(s"working really hard for: [$work]")
    }
  }))

  "The actor Master" should {
    "registers workers" in {
      val dummy = TestActorRef(new Actor {
        def receive = {
          case _ ⇒
        }
      })
      val master = newMaster
      val worker: ActorRef = newWorker(dummy)
      master ! RegisterWorker(worker)

      master.underlyingActor.workers should contain(worker)
    }

    "informs all workers that work is available when it receives some work" in {
      val master = newMaster
      val workers = Seq(TestProbe(), TestProbe())
      workers foreach { worker ⇒ master ! RegisterWorker(worker.ref)}

      master ! newEpic("someWork")
      workers.foreach {
        _.expectMsg(WorkAvailable)
      }
    }

    "responds with `CurrentlyBusy` if it's currently working on an epic" in {
      val master = newMasterWithWorker[String]
      master ! newEpic("someWork")
      expectNoMessage(500 millis)
      master ! newEpic("some other epic")
      expectMsg(CurrentlyBusy)
    }

    "should reset currentEpic if there's no more work" in {
      val master = newMasterWithWorker[String]
      val epicWithoutActualWork = newEpic(Seq[String]())

      master ! epicWithoutActualWork
      master ! GimmeWork
      expectMsg(Finished)
    }

    "tells worker next piece of work when they ask for it" in {
      val master = newMasterWithWorker[String]
      val someWork = "some work"
      master ! newEpic(someWork)

      master ! GimmeWork
      expectMsg(Work(someWork))
      master ! GimmeWork
    }

    "should tell the caller it's finished when there's no more work" in {
      val master = newMaster
      val worker = TestProbe()
      master ! RegisterWorker(worker.ref)

      val someWork = "some other work"
      master ! newEpic(someWork)

      worker.expectMsg(WorkAvailable)
      worker.send(master, GimmeWork)
      worker.expectMsg(Work(someWork))
      worker.send(master, GimmeWork)

      expectMsg(Finished)
    }

    "forgets about workers who died" in {
      val master = newMaster
      val worker = TestProbe()
      master ! RegisterWorker(worker.ref)
      master.underlyingActor.workers.size should be(1)

      worker.ref ! PoisonPill
      master.underlyingActor.workers.size should be(0)
    }

  }

  "The actor Worker" should {

    "registers with a master when created" in {
      val master = newMaster
      val worker = newWorker(master)

      master.underlyingActor.workers should contain(worker)
    }

    "asks for work when created (to ensure sure that a restarted actor will be busy straight away)" in {
      val worker = newWorker(testActor)
      fishForMessage() {
        case GimmeWork ⇒ true
        case _ ⇒ false
      }
    }

    "should request work from the master when there is work available" in {
      ignoreMsg { case _ ⇒ true}
      val worker = newWorker(testActor)

      ignoreNoMsg
      worker ! WorkAvailable
      expectMsg(GimmeWork)
    }

    "calls doWork when it receives work" in {
      val master = newMaster
      val work = "some work"
      var executedWork: String = null

      val worker = TestActorRef[Worker[String]](Props(new Worker[String](master) {
        def doWork(work: String): Future[_] =
          Future {
            executedWork = work
          }
      }))

      worker ! Work("some work")
      executedWork should be(work)
    }

    "should ask for more work once a piece of work is done" in {
      ignoreMsg { case _ ⇒ true}
      val worker = newWorker(testActor)

      ignoreNoMsg
      worker ! Work("")
      expectMsg(GimmeWork)
    }

  }
}
