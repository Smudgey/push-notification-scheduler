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

import akka.actor.Actor
import play.api.Logger
import scala.collection.{immutable, mutable}
import akka.actor.ActorRef
import WorkPullingPattern._
import scala.reflect.ClassTag
import akka.actor.Terminated
import scala.concurrent.Future

object WorkPullingPattern {
  type Batch[T] = Seq[T]
  sealed trait Message
  case class Epic[T](epic: immutable.Iterable[T]) extends Message //used by master to create work (in a streaming way)
  case object GimmeWork extends Message
  case object CurrentlyBusy extends Message
  case object WorkAvailable extends Message
  case class RegisterWorker(worker: ActorRef) extends Message
  case class Work[T](work: T) extends Message
  case object Finished extends Message
}

class Master[T] extends Actor {
  val workers = mutable.Set.empty[ActorRef]
  val activeWorkers = mutable.Map.empty[ActorRef, Boolean]

  override def receive: PartialFunction[Any, Unit] = idle orElse workerAdmin

  private def idle: Actor.Receive = {
    case epic: Epic[T] =>
      workers.foreach(_ ! WorkAvailable)
      become(busy(epic.epic.iterator, sender))

    case GimmeWork => Logger.debug("workers asked for work but we've no more work to do")
  }

  private def busy(currentEpic: Iterator[T], customer: ActorRef): Actor.Receive = {
    case epic: Epic[T] => sender ! CurrentlyBusy

    case GimmeWork ⇒
      if (currentEpic.hasNext) {
        sender ! Work(currentEpic.next())
        activeWorkers put (sender, true)
      }
      else {
        Logger.debug("Work Epic completed")
        activeWorkers put (sender, false)
        if (!activeWorkers.exists(_._2)) {
          customer ! Finished
          become(idle)
        }
      }
  }

  private def workerAdmin: Actor.Receive = {
    case RegisterWorker(worker) ⇒
      Logger.info(s"worker $worker registered")
      context.watch(worker)
      workers += worker

    case Terminated(worker) ⇒
      Logger.info(s"worker $worker died - taking off the set of workers")
      workers.remove(worker)
  }

  private val universalBehaviour = workerAdmin

  def become(newBehavior: Actor.Receive) {
    context.become(newBehavior orElse universalBehaviour)
  }

}

abstract class Worker[T: ClassTag](val master: ActorRef)(implicit manifest: Manifest[T]) extends Actor {
  implicit val ec = context.dispatcher

  override def preStart {
    master ! RegisterWorker(self)
    master ! GimmeWork
  }

  def receive: PartialFunction[Any, Unit] = {
    case WorkAvailable ⇒
      master ! GimmeWork
    case Work(work: T) ⇒
      // haven't found a nice way to get rid of that warning
      // looks like we can't suppress the erasure warning: http://stackoverflow.com/questions/3506370/is-there-an-equivalent-to-suppresswarnings-in-scala
      doWork(work) onComplete { case _ ⇒ master ! GimmeWork }
  }

  def doWork(work: T): Future[_]
}
