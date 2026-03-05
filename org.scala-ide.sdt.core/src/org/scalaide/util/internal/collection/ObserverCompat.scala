package org.scalaide.util.internal.collection

import scala.collection.mutable

trait Subscriber[-Evt, -Pub] {
  def notify(pub: Pub, event: Evt): Unit
}

trait Publisher[Evt] {
  type Pub = Publisher[Evt]

  private[this] val subscribers = mutable.Set.empty[Subscriber[Evt, Pub]]

  def subscribe(subscriber: Subscriber[Evt, Pub]): Unit = synchronized {
    subscribers += subscriber
  }

  def removeSubscription(subscriber: Subscriber[Evt, Pub]): Unit = synchronized {
    subscribers -= subscriber
  }

  def removeSubscriptions(): Unit = synchronized {
    subscribers.clear()
  }

  protected def publish(event: Evt): Unit = {
    val snapshot = synchronized(subscribers.toList)
    val publisher = this.asInstanceOf[Pub]
    snapshot.foreach(_.notify(publisher, event))
  }
}
